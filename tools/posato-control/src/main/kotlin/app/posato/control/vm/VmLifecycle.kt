package app.posato.control.vm

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.readKeychainSecret
import app.posato.control.desktop.AxBridgeBinary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText

/** A line of Tart VMs: its own golden VM, provisioning identifier, and per-run clone. */
enum class VmLine(
    val id: String,
    val goldenKey: ConfigurationKey
) {
    PRIMARY("primary", ConfigurationKey.VM_PRIMARY_GOLDEN),
    PEER("peer", ConfigurationKey.VM_PEER_GOLDEN),
    LEGACY("legacy", ConfigurationKey.VM_LEGACY_GOLDEN),
    VENTURA("ventura", ConfigurationKey.VM_VENTURA_GOLDEN),
    ;

    val cloneName: String
        get() = "posato-run-$id"

    companion object {
        fun parse(value: String): VmLine = entries.firstOrNull { it.id == value }
            ?: throw ControlException(ErrorCode.USAGE, "Unknown VM line '$value'.", "Use primary, peer, legacy, or ventura.")
    }
}

/**
 * Creates, provisions, and removes the per-run clone of a golden VM. The clone receives the staged development
 * package, the driver, and the prebuilt accessibility bridge on its own disk (code signatures do not validate from a
 * directory share) and reads a JDK from a read-only share.
 */
class VmLifecycle(
    private val context: RunContext
) {
    private val tart = Tart(context)

    fun create(
        line: VmLine,
        allowLowDisk: Boolean = false
    ): JsonObject {
        val golden = context.configuration.require(line.goldenKey, ErrorCode.VM_UNAVAILABLE, "Creating the ${line.id} VM")
        val diskWarning = lowDiskWarning(lowestFreeSpace(context.layout.root), allowLowDisk)
        diskWarning?.let { context.log("Creating ${line.cloneName} although $it") }
        refuseClone(tart.list(), golden, line.cloneName) { name -> describeCloneOwner(name, readCloneOwner(name), context.layout.root) }
        val jdk = hostJdk(context)
        tart.clone(golden, line.cloneName)
        recordCloneOwner(context, line.cloneName)
        Files.deleteIfExists(accountAttentionMarker(line))
        val log = ownerOnlyFile(vmDirectory(line).resolve(RUN_LOG))
        val started = System.currentTimeMillis()
        tart.start(line.cloneName, jdk, log)
        vmEndpoint(line, BOOT_TIMEOUT_MS)
        val vncHold = VncHold(context, line).start()
        waitForAgent(line)
        sync(line)
        val created = mapOf(
            "vm" to JsonPrimitive(line.cloneName),
            "golden" to JsonPrimitive(golden),
            "readyMs" to JsonPrimitive(System.currentTimeMillis() - started),
            "vncHold" to JsonPrimitive(vncHold),
        )
        return JsonObject(if (diskWarning == null) created else created + ("diskSpaceWarning" to JsonPrimitive(diskWarning)))
    }

    /**
     * Copies the driver distribution, fixtures, the prebuilt bridge, and the staged package when there is one into the
     * guest, and stamps which package it copied. Once a candidate is installed, only the driver travels, and
     * LaunchServices must still know no other Posato bundle.
     */
    fun sync(line: VmLine) {
        requireRunning(line)
        AxBridgeBinary(context).ensureBuilt()
        val layout = context.layout
        val candidate = CandidateInstall(context)
        val candidateInstalled = candidate.installed(line)
        // A notarized candidate installed later needs only the driver, so a clone may start without a staged package.
        val copyPackage = !candidateInstalled && layout.stagedDesktopApplication.exists()
        // Hashed before the copy, so a build that runs meanwhile cannot stamp another package than the one copied.
        val fingerprint = if (copyPackage) packageFingerprint(layout.stagedDesktopApplication) else null
        val driver = driverPaths(layout)
        val tooling = toolingFingerprint(layout.root, executedDriverPaths(layout))
        val paths = if (copyPackage) driver + layout.relativize(layout.stagedDesktopApplication) else driver
        tart.pipe(
            line.cloneName,
            "tar -C ${shellQuote(layout.root.toString())} -cf - " + paths.joinToString(" ") { shellQuote(it) },
            // The old stamp goes first, so a copy that fails midway leaves a guest that reads as outdated.
            "rm -f \"$GUEST_ROOT/$GUEST_TOOLING_STAMP\" && rm -rf $GUEST_ROOT/tools $GUEST_ROOT/desktopApp && " +
                "mkdir -p $GUEST_ROOT && tar -C $GUEST_ROOT -xf -",
            if (copyPackage) "Copying the package and driver into ${line.cloneName}" else "Copying the driver into ${line.cloneName}",
        )
        val stamp = "\"$GUEST_ROOT/$GUEST_PACKAGE_STAMP\""
        val record = "mkdir -p \"$GUEST_ROOT/build/verification\" && printf %s $tooling > \"$GUEST_ROOT/$GUEST_TOOLING_STAMP\" && " +
            if (fingerprint != null) "printf %s $fingerprint > $stamp" else "rm -f $stamp"
        tart.exec(line.cloneName, record).requireSuccess(ErrorCode.VM_UNAVAILABLE, "Recording the synced package in ${line.cloneName}")
        if (candidateInstalled) GuestRegistrations(context).requireSingleBundle(line)
    }

    /**
     * Stops a command that starts the development package when the host staged a package the guest has not received
     * yet. An installed candidate is what such a guest launches, so it needs no check.
     */
    fun requireCurrentPackage(line: VmLine) {
        val staged = context.layout.stagedDesktopApplication.takeIf { it.exists() } ?: return
        val read = "if test -f \"$GUEST_ROOT/${CandidateInstall.MARKER}\"; then echo candidate; " +
            "else cat \"$GUEST_ROOT/$GUEST_PACKAGE_STAMP\" 2>/dev/null || true; fi"
        val guest = tart.exec(line.cloneName, read)
            .requireSuccess(ErrorCode.VM_UNAVAILABLE, "Reading the synced package in ${line.cloneName}")
            .stdout.trim()
        if (guest == "candidate") return
        refuseOutdatedPackage(guest.ifEmpty { null }, packageFingerprint(staged), line)
    }

    /**
     * Boots the line's existing VM headless without cloning, for preparing a golden image under the clone's name
     * before `tart rename`. It waits only for the VNC address: the guest agent may not be installed yet.
     */
    fun boot(line: VmLine): JsonObject {
        refuseBoot(tart.list(), context.configuration.value(line.goldenKey), line.cloneName)
        Files.deleteIfExists(accountAttentionMarker(line))
        val log = ownerOnlyFile(vmDirectory(line).resolve(RUN_LOG))
        tart.start(line.cloneName, hostJdk(context), log)
        vmEndpoint(line, BOOT_TIMEOUT_MS)
        val vncHold = VncHold(context, line).start()
        return JsonObject(mapOf("vm" to JsonPrimitive(line.cloneName), "vncHold" to JsonPrimitive(vncHold)))
    }

    /** Shuts the guest down from inside, so its last writes reach the disk, and keeps the VM; true when it was forced. */
    fun shutdown(line: VmLine): Boolean {
        if (!running(line)) return false
        tart.exec(line.cloneName, "sudo -S -p '' /bin/sh -c 'sync; /sbin/shutdown -h +0'", stdin = vmAdminPassword(context) + "\n")
        return awaitStopped(line)
    }

    /**
     * Shuts the guest down from inside, so its last writes reach the disk, then deletes the clone and, with its Tart
     * directory, its owner marker. Refuses a clone whose Posato is still linked to an iCloud workspace unless
     * [keepWorkspace]: the workspace and its key would stay in the shared test account, and a later clone of the same
     * line cannot read that key (`observed` 2026-09-25). Press **Remove workspace** in the app first.
     */
    fun destroy(
        line: VmLine,
        keepWorkspace: Boolean = false
    ) {
        if (tart.list().none { it.name == line.cloneName }) return
        if (running(line) && !keepWorkspace) {
            val linked = tart.exec(line.cloneName, LINKED_WORKSPACE_QUERY)
            refuseLinkedWorkspace(line, linkedWorkspaces(linked.stdout))
        }
        if (running(line)) {
            tart.exec(line.cloneName, "sudo -S -p '' /bin/sh -c 'sync; /sbin/shutdown -h +0'", stdin = vmAdminPassword(context) + "\n")
            awaitStopped(line)
        }
        tart.delete(line.cloneName)
        vmDirectory(line).toFile().deleteRecursively()
    }

    fun running(line: VmLine): Boolean = tart.list().any { it.name == line.cloneName && it.running }

    fun requireRunning(line: VmLine) {
        if (!running(line)) {
            throw ControlException(
                ErrorCode.VM_UNAVAILABLE,
                "The ${line.id} VM '${line.cloneName}' is not running.",
                "Run `posato-control vm create --line ${line.id}`.",
            )
        }
    }

    private fun waitForAgent(line: VmLine) {
        val deadline = System.currentTimeMillis() + AGENT_TIMEOUT_MS
        while (!agentAnswers(line)) {
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(
                    ErrorCode.VM_UNAVAILABLE,
                    "The guest agent in ${line.cloneName} did not answer.",
                    "Check that the golden VM logs in automatically and runs tart-guest-agent.",
                )
            }
            Thread.sleep(POLL_MS)
        }
    }

    /** A probe that times out means the agent is not up yet; the loop above bounds the wait. */
    private fun agentAnswers(line: VmLine): Boolean = try {
        tart.exec(line.cloneName, "true", timeout = AGENT_PROBE).exitCode == 0
    } catch (exception: ControlException) {
        context.log("The guest agent is not answering yet: ${exception.message}")
        false
    }

    /** True when the guest did not stop by itself and `tart stop` had to force it, which can lose its last writes. */
    private fun awaitStopped(line: VmLine): Boolean {
        val deadline = System.currentTimeMillis() + SHUTDOWN_TIMEOUT_MS
        while (running(line)) {
            if (System.currentTimeMillis() >= deadline) {
                tart.stop(line.cloneName)
                return true
            }
            Thread.sleep(POLL_MS)
        }
        return false
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val BOOT_TIMEOUT_MS = 60_000L
        const val AGENT_TIMEOUT_MS = 240_000L
        const val SHUTDOWN_TIMEOUT_MS = 180_000L
        val AGENT_PROBE: Duration = Duration.ofSeconds(8)
    }
}

/** The guest directory that holds the driver, fixtures, and evidence; `$HOME` expands in double quotes too. */
internal const val GUEST_ROOT = "\$HOME/posato-run"

/**
 * An APFS clone of the host JDK at a path without `@`, which Tart's directory-share parser rejects. A running guest
 * reads it through a directory share, so it sits beside the per-clone state rather than in a checkout. Its name holds
 * the JDK's runtime version and a digest of its resolved bundle path and `release` file, so a host JDK update, which
 * can keep the bundle's name (Homebrew's `openjdk.jdk`), gets a fresh copy while running guests keep the old one. It
 * is copied under a temporary name and moved into place, so two checkouts creating it at once each see a whole copy.
 */
internal fun hostJdk(context: RunContext): Path {
    val home = context.subprocess.run(
        listOf("/usr/libexec/java_home"),
    ).requireSuccess(ErrorCode.COMMAND_FAILED, "Finding the host JDK").stdout.trim()
    val bundle = Path.of(home).toRealPath().parent.parent
    val target = stateRoot().resolve(jdkCopyName(bundle))
    if (completeJdk(target)) return target
    Files.createDirectories(stateRoot(), OWNER_ONLY_DIRECTORY)
    val temporary = Files.createTempDirectory(stateRoot(), "jdk-copy")
    try {
        val copy = temporary.resolve("jdk")
        context.subprocess.run(
            listOf("/bin/cp", "-cR", bundle.toString(), copy.toString()),
        ).requireSuccess(ErrorCode.COMMAND_FAILED, "Cloning the host JDK")
        try {
            Files.move(copy, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (exception: FileSystemException) {
            // macOS reports a rename onto a populated directory as a plain "Directory not empty".
            if (!completeJdk(target)) throw exception
            context.log("Another checkout placed $target first; keeping its copy.")
        }
    } finally {
        temporary.toFile().deleteRecursively()
    }
    return target
}

private fun jdkCopyName(bundle: Path): String {
    val release = bundle.resolve("Contents/Home/release").readText()
    val version = Regex("""^JAVA_RUNTIME_VERSION="([^"]+)"""", RegexOption.MULTILINE).find(release)?.groupValues?.get(1)
        ?: Regex("""^JAVA_VERSION="([^"]+)"""", RegexOption.MULTILINE).find(release)?.groupValues?.get(1)
        ?: "unknown"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$bundle\n$release".toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "jdk-${version.replace(Regex("[^A-Za-z0-9._-]"), "_")}-${digest.take(JDK_DIGEST_LENGTH)}"
}

private fun completeJdk(copy: Path): Boolean = Files.isExecutable(copy.resolve("Contents/Home/bin/java"))

private const val JDK_DIGEST_LENGTH = 12

internal fun ownerOnlyFile(file: Path): Path {
    Files.createDirectories(file.parent, OWNER_ONLY_DIRECTORY)
    Files.deleteIfExists(file)
    return Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
}

private const val RUN_LOG = "tart-run.log"
private const val ENDPOINT_POLL_MS = 1_000L

/**
 * Per-clone state, such as the Tart log holding the VNC address and password (owner-only). Clone names are
 * machine-wide, so their state is too: it sits in Tart's home beside the clones, not in a checkout, and outlives the
 * worktree that created or booted the VM (a removed worktree once took a running VM's address with it, `observed`
 * 2026-10-08). It stays outside the clone's own Tart directory, which `tart rename` would carry into a golden VM.
 */
internal fun vmDirectory(line: VmLine): Path = stateRoot().resolve(line.cloneName)

/** The driver's machine-wide VM state in Tart's home; owner-only, since screen captures and the VNC log pass through it. */
private fun stateRoot(): Path = tartHome().resolve("posato-control")

private val OWNER_ONLY_DIRECTORY = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))

internal fun vmEndpoint(
    line: VmLine,
    timeoutMs: Long
): VncEndpoint {
    val log = vmDirectory(line).resolve(RUN_LOG)
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        val endpoint = if (log.exists()) parseVncEndpoint(log.readText()) else null
        if (endpoint != null) return endpoint
        if (System.currentTimeMillis() >= deadline) {
            throw ControlException(
                ErrorCode.VM_UNAVAILABLE,
                "The ${line.id} VM has no VNC address.",
                "Destroy and create it again with posato-control.",
            )
        }
        Thread.sleep(ENDPOINT_POLL_MS)
    }
}

internal fun vmAdminPassword(context: RunContext): String = readKeychainSecret(
    context,
    ConfigurationKey.VM_ADMIN_KEYCHAIN_SERVICE,
    ConfigurationKey.VM_ADMIN_KEYCHAIN_ACCOUNT,
    "the guest administrator password",
)
