package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.name

/** What `vm install` observed while installing one notarized candidate the way a person does. */
@Serializable
data class CandidateInstallation(
    val vm: String,
    val dmg: String,
    val dmgSha256: String,
    val bundleIdentifier: String,
    val version: String,
    val build: String,
    val signingAuthority: String?,
    val teamIdentifier: String?,
    val gatekeeper: String,
    val quarantineBeforeOpen: String,
    val quarantineAfterOpen: String,
    val firstOpenExecutable: String,
    val unregistered: List<String>,
    val registrations: List<String>,
    val replaced: String? = null,
)

/**
 * Installs a notarized candidate DMG in a clone as a person would: the image arrives quarantined, Finder copies the
 * application into `/Applications` by drag and drop, and Gatekeeper's first-open question is answered over VNC. A
 * copy made any other way is translocated at launch (`observed` 2026-09-25), and Sparkle refuses to update a
 * translocated application. Afterwards LaunchServices knows no other Posato bundle, and desktop commands in the guest
 * drive the installed candidate instead of the synced development package. With `replace`, an installed older build
 * first goes to the guest user's Trash, as Finder's Replace does, so that the manual move between releases keeps the
 * user's data.
 */
class CandidateInstall(
    private val context: RunContext
) {
    private val tart = Tart(context)

    fun install(
        line: VmLine,
        dmg: Path,
        applicationLabel: String,
        applicationsLabel: String,
        timeoutMs: Long,
        replace: Boolean = false
    ): CandidateInstallation {
        VmLifecycle(context).requireRunning(line)
        val name = candidateFileName(dmg)
        if (!dmg.exists()) throw ControlException(ErrorCode.USAGE, "No candidate image at $dmg.")
        val guestDmg = "$GUEST_CANDIDATES/$name"
        val moveAside = preflight(line, replace)
        tart.pipe(
            line.cloneName,
            "cat ${shellQuote(dmg.toAbsolutePath().toString())}",
            "mkdir -p \"$GUEST_CANDIDATES\" && cat > \"$guestDmg\"",
            "Copying $name into ${line.cloneName}",
        )
        guest(
            line,
            "xattr -w com.apple.quarantine \"0081;\$(printf %x \$(date +%s));posato-control;\$(uuidgen)\" \"$guestDmg\" && open \"$guestDmg\"",
            "Quarantining and opening $name",
        )
        val replaced = if (moveAside) moveToTrash(line) else null
        VmPrompts(context).drag(line, applicationLabel, 0, applicationsLabel, 0, timeoutMs)
        awaitGuest(line, "/usr/bin/codesign --verify --deep --strict $INSTALLED_APPLICATION", timeoutMs, "Finder's copy into /Applications")
        detach(line, guestDmg)
        guest(line, "rm -rf \"$GUEST_ROOT/desktopApp\"", "Removing the synced development package")
        val launchServices = GuestRegistrations(context)
        val unregistered = launchServices.retireOtherBundles(line)
        val registrations = launchServices.requireSingleBundle(line)
        val facts = bundleFacts(line)
        val gatekeeper = guestOutput(line, "/usr/sbin/spctl -a -vv -t exec $INSTALLED_APPLICATION 2>&1")
            .requireSuccess(ErrorCode.INSTALL_FAILED, "Gatekeeper's assessment of the candidate").stdout.trim()
        val signing = guestOutput(line, "/usr/bin/codesign -dv --verbose=2 $INSTALLED_APPLICATION 2>&1").stdout
        val quarantineBeforeOpen = quarantine(line)
        val executable = firstOpen(line, facts.getValue(BUNDLE_IDENTIFIER), timeoutMs)
        guest(
            line,
            "mkdir -p \"$GUEST_ROOT/build/verification\" && printf %s $INSTALLED_APPLICATION > \"$GUEST_ROOT/$MARKER\"",
            "Pointing the guest's desktop commands at the installed candidate",
        )
        val installation = CandidateInstallation(
            vm = line.cloneName,
            dmg = name,
            dmgSha256 = sha256(dmg),
            bundleIdentifier = facts.getValue(BUNDLE_IDENTIFIER),
            version = facts.getValue(BUNDLE_VERSION),
            build = facts.getValue(BUNDLE_BUILD),
            signingAuthority = signingDetail(signing, "Authority="),
            teamIdentifier = signingDetail(signing, "TeamIdentifier="),
            gatekeeper = gatekeeper,
            quarantineBeforeOpen = quarantineBeforeOpen,
            quarantineAfterOpen = quarantine(line),
            firstOpenExecutable = executable,
            unregistered = unregistered,
            registrations = registrations,
            replaced = replaced,
        )
        val evidence = context.artifactPath("candidate-install.json")
        Files.createDirectories(evidence.parent)
        Files.writeString(evidence, ControlJson.pretty.encodeToString(CandidateInstallation.serializer(), installation))
        context.recordArtifact(evidence)
        return installation
    }

    /** True once `vm install` has replaced the development package in this clone. */
    fun installed(line: VmLine): Boolean = guestOutput(line, "if test -f \"$GUEST_ROOT/$MARKER\"; then echo yes; else echo no; fi")
        .requireSuccess(ErrorCode.VM_UNAVAILABLE, "Checking ${line.cloneName} for an installed candidate")
        .stdout.trim() == "yes"

    /** Applies [requireInstallable] to the clone; true when an installed build has to be moved aside. */
    private fun preflight(
        line: VmLine,
        replace: Boolean
    ): Boolean = requireInstallable(
        line.cloneName,
        running = tart.exec(line.cloneName, "/usr/bin/pgrep -x Posato").exitCode == 0,
        installed = tart.exec(line.cloneName, "test -e $INSTALLED_APPLICATION").exitCode == 0,
        replace = replace,
    )

    /**
     * Moves the installed build to the guest user's Trash right before the drag, where a person meets Finder's Replace,
     * and returns its version; a failure before this point leaves the installed build in place.
     */
    private fun moveToTrash(line: VmLine): String {
        val previous = bundleFacts(line)
        val version = listOf(BUNDLE_VERSION, BUNDLE_BUILD).map { key ->
            previous[key] ?: throw ControlException(ErrorCode.INSTALL_FAILED, "The installed Posato has no $key.")
        }
        val replaced = "${version[0]} (${version[1]})"
        guest(
            line,
            "mkdir -p \"\$HOME/.Trash\" && stamp=\$(date +%s) && /bin/mv $INSTALLED_APPLICATION \"\$HOME/.Trash/Posato \$stamp.app\"",
            "Moving the installed Posato $replaced to the Trash",
        )
        return replaced
    }

    private fun detach(
        line: VmLine,
        guestDmg: String
    ) {
        val image = guestOutput(line, "/bin/realpath \"$guestDmg\"")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Resolving the image path")
            .stdout.trim()
        val info = guestOutput(line, "/usr/bin/hdiutil info -plist | /usr/bin/plutil -convert json -o - -")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Listing attached images").stdout
        val mountPoints = mountPointsOf(info, image)
        if (mountPoints.isEmpty()) {
            throw ControlException(
                ErrorCode.INSTALL_FAILED,
                "No mounted volume of $image was found to eject.",
                "Destroy the clone and install again.",
            )
        }
        mountPoints.forEach { mountPoint ->
            guest(line, "/usr/bin/hdiutil detach ${shellQuote(mountPoint)}", "Ejecting $mountPoint")
        }
    }

    private fun bundleFacts(line: VmLine): Map<String, String> {
        val keys = listOf(BUNDLE_IDENTIFIER, BUNDLE_VERSION, BUNDLE_BUILD)
        val script = keys.joinToString("; ") { key ->
            "printf '%s=' $key; /usr/libexec/PlistBuddy -c 'Print :$key' $INSTALLED_APPLICATION/Contents/Info.plist"
        }
        val output = guestOutput(line, script).requireSuccess(ErrorCode.INSTALL_FAILED, "Reading the candidate's Info.plist").stdout
        return output.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=').trim() }
    }

    private fun quarantine(line: VmLine): String =
        guestOutput(line, "/usr/bin/xattr -p com.apple.quarantine $INSTALLED_APPLICATION 2>&1").stdout.trim()

    /**
     * Opens the candidate through LaunchServices, answers Gatekeeper, and requires that the process runs from
     * `/Applications` rather than a translocated copy; then quits it so the first tracked launch starts cleanly.
     */
    private fun firstOpen(
        line: VmLine,
        bundleIdentifier: String,
        timeoutMs: Long
    ): String {
        guest(line, "/usr/bin/open $INSTALLED_APPLICATION", "Opening the candidate")
        VmPrompts(context).answer(line, GuestPrompt.GATEKEEPER, timeoutMs)
        awaitGuest(line, "/usr/bin/pgrep -x Posato", timeoutMs, "The candidate's first launch")
        val executable = guestOutput(line, "/bin/ps -o comm= -p \$(/usr/bin/pgrep -x Posato | head -n 1)").stdout.trim()
        guest(line, "/usr/bin/osascript -e ${shellQuote("quit app id \"$bundleIdentifier\"")}", "Quitting the candidate after its first launch")
        awaitGuest(line, "! /usr/bin/pgrep -x Posato", timeoutMs, "The candidate's exit after its first launch")
        if (executable != INSTALLED_EXECUTABLE) {
            throw ControlException(
                ErrorCode.INSTALL_FAILED,
                "The candidate ran from $executable instead of $INSTALLED_EXECUTABLE.",
                "A translocated application cannot be updated; install it by drag and drop into a fresh clone.",
            )
        }
        return executable
    }

    private fun awaitGuest(
        line: VmLine,
        script: String,
        timeoutMs: Long,
        what: String
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (tart.exec(line.cloneName, script).exitCode != 0) {
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(ErrorCode.WAIT_TIMEOUT, "$what did not finish within ${timeoutMs / MILLIS_PER_SECOND} s.")
            }
            Thread.sleep(POLL_MS)
        }
    }

    private fun guest(
        line: VmLine,
        script: String,
        what: String
    ) {
        guestOutput(line, script).requireSuccess(ErrorCode.INSTALL_FAILED, what)
    }

    private fun guestOutput(
        line: VmLine,
        script: String
    ): ProcessOutput = tart.exec(line.cloneName, script)

    private companion object {
        const val GUEST_CANDIDATES = "$GUEST_ROOT/candidates"
        const val MARKER = "build/verification/desktop-application"
        const val INSTALLED_EXECUTABLE = "$INSTALLED_APPLICATION/Contents/MacOS/Posato"
        const val BUNDLE_IDENTIFIER = "CFBundleIdentifier"
        const val BUNDLE_VERSION = "CFBundleShortVersionString"
        const val BUNDLE_BUILD = "CFBundleVersion"
        const val POLL_MS = 1_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

private fun sha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { stream ->
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private const val BUFFER_BYTES = 1 shl 16

/** Where `vm install` puts the candidate; Sparkle replaces the bundle at this path. */
internal const val INSTALLED_APPLICATION = "/Applications/Posato.app"

private val CANDIDATE_NAME = Regex("""[A-Za-z0-9._-]+\.dmg""")

/**
 * Decides whether `vm install` may proceed: never while Posato runs, and over an installed build only with [replace].
 * Returns true when the installed build has to be moved aside first.
 */
internal fun requireInstallable(
    clone: String,
    running: Boolean,
    installed: Boolean,
    replace: Boolean
): Boolean {
    if (running) {
        throw ControlException(ErrorCode.ALREADY_RUNNING, "A Posato process runs in $clone.", "Quit it before installing a candidate.")
    }
    if (installed && !replace) {
        throw ControlException(
            ErrorCode.INSTALL_FAILED,
            "$clone already has $INSTALLED_APPLICATION.",
            "Pass --replace for the manual move from an installed release, or install into a fresh clone.",
        )
    }
    return installed
}

/** The candidate's file name, restricted so that it can travel unquoted into guest scripts. */
internal fun candidateFileName(dmg: Path): String {
    val name = dmg.name
    if (!CANDIDATE_NAME.matches(name)) {
        throw ControlException(
            ErrorCode.USAGE,
            "The candidate image '$name' needs a plain .dmg file name.",
            "Use letters, digits, dots, dashes, or underscores.",
        )
    }
    return name
}

/** The mount points of [image] in `hdiutil info -plist` converted to JSON. */
internal fun mountPointsOf(
    hdiutilInfo: String,
    image: String
): List<String> {
    val images = ControlJson.lenient.parseToJsonElement(hdiutilInfo).jsonObject["images"]?.jsonArray ?: return emptyList()
    return images
        .map { it.jsonObject }
        .filter { it["image-path"]?.jsonPrimitive?.content == image }
        .flatMap { entry -> entry["system-entities"]?.jsonArray.orEmpty() }
        .mapNotNull { it.jsonObject["mount-point"]?.jsonPrimitive?.content }
}

/** The first `codesign -dv` line with [prefix], without the prefix. */
internal fun signingDetail(
    details: String,
    prefix: String
): String? = details.lineSequence().map { it.trim() }.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
