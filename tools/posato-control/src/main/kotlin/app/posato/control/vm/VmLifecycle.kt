package app.posato.control.vm

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.readKeychainSecret
import app.posato.control.desktop.AxBridgeBinary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
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
    ;

    val cloneName: String
        get() = "posato-run-$id"

    companion object {
        fun parse(value: String): VmLine = entries.firstOrNull { it.id == value }
            ?: throw ControlException(ErrorCode.USAGE, "Unknown VM line '$value'.", "Use primary, peer, or legacy.")
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

    fun create(line: VmLine): JsonObject {
        val golden = context.configuration.require(line.goldenKey, ErrorCode.VM_UNAVAILABLE, "Creating the ${line.id} VM")
        refuseClone(tart.list(), golden, line.cloneName)
        val jdk = hostJdk(context)
        tart.clone(golden, line.cloneName)
        val log = ownerOnlyFile(vmDirectory(context, line).resolve(RUN_LOG))
        val started = System.currentTimeMillis()
        tart.start(line.cloneName, jdk, log)
        vmEndpoint(context, line, BOOT_TIMEOUT_MS)
        waitForAgent(line)
        sync(line)
        return JsonObject(
            mapOf(
                "vm" to JsonPrimitive(line.cloneName),
                "golden" to JsonPrimitive(golden),
                "readyMs" to JsonPrimitive(System.currentTimeMillis() - started),
            ),
        )
    }

    /**
     * Copies the staged package, the driver distribution, fixtures, and the prebuilt bridge into the guest. Once a
     * candidate is installed, only the driver travels, and LaunchServices must still know no other Posato bundle.
     */
    fun sync(line: VmLine) {
        requireRunning(line)
        AxBridgeBinary(context).ensureBuilt()
        val layout = context.layout
        val candidate = CandidateInstall(context)
        val candidateInstalled = candidate.installed(line)
        if (!candidateInstalled && !layout.stagedDesktopApplication.exists()) {
            throw ControlException(ErrorCode.APP_NOT_STAGED, "No staged desktop package.", "Run `posato-control build -t desktop` first.")
        }
        val driver = listOf(
            "settings.gradle.kts",
            "tools/posato-control/build/install",
            "tools/posato-control/native",
            "tools/posato-control/fixtures",
            layout.relativize(layout.accessibilityBridgeBinary),
        )
        val paths = if (candidateInstalled) driver else driver + layout.relativize(layout.stagedDesktopApplication)
        tart.pipe(
            line.cloneName,
            "tar -C ${shellQuote(layout.root.toString())} -cf - " + paths.joinToString(" ") { shellQuote(it) },
            "rm -rf $GUEST_ROOT/tools $GUEST_ROOT/desktopApp && mkdir -p $GUEST_ROOT && tar -C $GUEST_ROOT -xf -",
            "Copying the package and driver into ${line.cloneName}",
        )
        if (candidateInstalled) GuestRegistrations(context).requireSingleBundle(line)
    }

    /**
     * Boots the line's existing VM headless without cloning, for preparing a golden image under the clone's name
     * before `tart rename`. It waits only for the VNC address: the guest agent may not be installed yet.
     */
    fun boot(line: VmLine): JsonObject {
        if (tart.list().none { it.name == line.cloneName }) {
            throw ControlException(ErrorCode.VM_UNAVAILABLE, "No VM named ${line.cloneName} exists.", "Create it with tart create or vm create.")
        }
        if (running(line)) throw ControlException(ErrorCode.VM_UNAVAILABLE, "${line.cloneName} already runs.")
        val log = ownerOnlyFile(vmDirectory(context, line).resolve(RUN_LOG))
        tart.start(line.cloneName, hostJdk(context), log)
        vmEndpoint(context, line, BOOT_TIMEOUT_MS)
        return JsonObject(mapOf("vm" to JsonPrimitive(line.cloneName)))
    }

    /** Shuts the guest down from inside, so its last writes reach the disk, and keeps the VM. */
    fun shutdown(line: VmLine) {
        if (!running(line)) return
        tart.exec(line.cloneName, "sudo -S -p '' /bin/sh -c 'sync; /sbin/shutdown -h +0'", stdin = vmAdminPassword(context) + "\n")
        awaitStopped(line)
    }

    /** Shuts the guest down from inside, so its last writes reach the disk, then deletes the clone. */
    fun destroy(line: VmLine) {
        if (tart.list().none { it.name == line.cloneName }) return
        if (running(line)) {
            tart.exec(line.cloneName, "sudo -S -p '' /bin/sh -c 'sync; /sbin/shutdown -h +0'", stdin = vmAdminPassword(context) + "\n")
            awaitStopped(line)
        }
        tart.delete(line.cloneName)
        vmDirectory(context, line).toFile().deleteRecursively()
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

    private fun awaitStopped(line: VmLine) {
        val deadline = System.currentTimeMillis() + SHUTDOWN_TIMEOUT_MS
        while (running(line)) {
            if (System.currentTimeMillis() >= deadline) {
                tart.stop(line.cloneName)
                return
            }
            Thread.sleep(POLL_MS)
        }
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

/** An APFS clone of the host JDK at a path without `@`, which Tart's directory-share parser rejects. */
internal fun hostJdk(context: RunContext): Path {
    val target = context.layout.verificationDirectory.resolve("vm").resolve("jdk")
    if (target.exists()) return target
    val home = context.subprocess.run(
        listOf("/usr/libexec/java_home"),
    ).requireSuccess(ErrorCode.COMMAND_FAILED, "Finding the host JDK").stdout.trim()
    val bundle = Path.of(home).parent.parent
    Files.createDirectories(target.parent)
    context.subprocess.run(
        listOf("/bin/cp", "-cR", bundle.toString(), target.toString()),
    ).requireSuccess(ErrorCode.COMMAND_FAILED, "Cloning the host JDK")
    return target
}

internal fun ownerOnlyFile(file: Path): Path {
    Files.createDirectories(file.parent)
    Files.deleteIfExists(file)
    return Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
}

private const val RUN_LOG = "tart-run.log"
private const val ENDPOINT_POLL_MS = 1_000L

/** Per-line state under the ignored verification directory; the Tart log holding the VNC password is owner-only. */
internal fun vmDirectory(
    context: RunContext,
    line: VmLine
): Path = context.layout.verificationDirectory.resolve("vm").resolve(line.id)

internal fun vmEndpoint(
    context: RunContext,
    line: VmLine,
    timeoutMs: Long
): VncEndpoint {
    val log = vmDirectory(context, line).resolve(RUN_LOG)
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
