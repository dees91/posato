package app.posato.control.windows

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import app.posato.control.vm.MachineLedger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

/**
 * The Windows clone of `PLATFORM-002`'s verification: a Tart Windows 11 guest made from the golden VM that
 * docs/development/unattended-verification.md describes. Commands run as PowerShell scripts in the signed-in
 * administrator's console session, so they reach the desktop. The guest agent passes only the low byte of a command's
 * exit code, so a script that reports a larger code, such as msiexec's, prints it. [tools] copies files in and out,
 * reads Posato through the Java Access Bridge, and runs the guest checks.
 */
class WindowsGuest(
    private val context: RunContext,
) {
    private val tart by lazy { WindowsTart(context) }
    private val ledger = MachineLedger.of(context)
    private val golden = context.configuration.value(ConfigurationKey.VM_WINDOWS_GOLDEN) ?: DEFAULT_GOLDEN

    val tools: WindowsTools = WindowsTools(this)

    fun create(): String {
        if (tart.list().any { it.name == CLONE }) throw ControlException(ErrorCode.ALREADY_EXISTS, "$CLONE already exists; `windows destroy` first.")
        if (!goldenExists()) {
            throw ControlException(
                ErrorCode.VM_UNAVAILABLE,
                "No Windows golden VM $golden in ${tart.home}.",
                "Create it as docs/development/unattended-verification.md describes, or set ${ConfigurationKey.VM_WINDOWS_GOLDEN.propertyName}.",
            )
        }
        ledger.claim(
            CLONE,
            tart.home.resolve("vms").resolve(CLONE),
            context.layout.root,
            belowReserve = false,
            memoryMib = tart.memoryMib(golden),
            command = "windows create",
        )
        // No clone, so its lease would only hold back the reserve until it expires.
        runCatching { tart.clone(golden, CLONE) }.onFailure { ledger.release(CLONE) }.getOrThrow()
        return boot()
    }

    /** Starts the existing clone, waits for its guest agent, and copies the driver's guest scripts in. */
    fun boot(): String {
        if (running()) return CLONE
        val share = syncShare(context)
        Files.createDirectories(share)
        tart.start(CLONE, listOf("$SHARE_NAME:$share"), context.layout.verificationDirectory.resolve("windows-run.log"))
        val deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val probe = runCatching { tart.exec(CLONE, listOf("cmd", "/c", "ver"), timeout = Duration.ofSeconds(PROBE_SECONDS)) }.getOrNull()
            if (probe?.succeeded == true) {
                quietBackgroundWork()
                tools.installScripts()
                return CLONE
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw ControlException(
            ErrorCode.VM_UNAVAILABLE,
            "The guest agent in $CLONE did not answer in time.",
            "Read build/verification/windows-run.log.",
        )
    }

    /**
     * A clone left running for more than a few minutes starts Windows Update and a Defender scan, which starve the
     * guest until a PowerShell start takes over a minute. The clone is disposable, so this stops and disables the
     * update services through `sc.exe`, which needs no PowerShell, and keeps Defender out of the driver's folders.
     */
    private fun quietBackgroundWork() {
        val services = listOf("wuauserv", "UsoSvc", "WaaSMedicSvc", "TrustedInstaller")
        val script = services.joinToString(" & ") { "sc.exe config $it start= disabled >nul 2>&1 & sc.exe stop $it >nul 2>&1" }
        tart.exec(CLONE, listOf("cmd", "/c", "$script & exit 0"))
        powershell(
            "Add-MpPreference -ExclusionPath ${psQuote(TOOLS)},'C:\\Windows\\Temp' -ErrorAction SilentlyContinue\n" +
                "Add-MpPreference -ExclusionProcess 'powershell.exe','python.exe','java.exe','javaw.exe' -ErrorAction SilentlyContinue",
        )
    }

    fun stop() {
        tart.stop(CLONE)
    }

    fun running(): Boolean = tart.list().any { it.name == CLONE && it.running }

    fun goldenExists(): Boolean = tart.list().any { it.name == golden }

    fun requireRunning() {
        if (!running()) {
            throw ControlException(ErrorCode.VM_UNAVAILABLE, "The Windows clone $CLONE is not running.", "Run `windows create` (or `windows boot`).")
        }
    }

    /** Deletes the clone and ends its lease, also when the clone was deleted outside posato-control. */
    fun destroy() {
        if (tart.list().any { it.name == CLONE }) {
            tart.stop(CLONE)
            tart.delete(CLONE)
        }
        ledger.release(CLONE)
    }

    /**
     * Runs a PowerShell script in the signed-in session and returns its output with Windows line ends turned into
     * `\n`. Windows PowerShell 5.1 goes on after a failing native command, so a script that cares checks
     * `$LASTEXITCODE` itself.
     */
    fun powershell(
        script: String,
        stdin: ByteArray? = null,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): ProcessOutput {
        val encoded = Base64.getEncoder().encodeToString("$QUIET\n$script".toByteArray(Charsets.UTF_16LE))
        require(encoded.length <= MAX_ENCODED) { "The guest script is too long for one command line; ship it as a resource script instead." }
        return run(POWERSHELL + listOf("-EncodedCommand", encoded), stdin, timeout)
    }

    /**
     * Whether UAC is on in the clone (the golden VM has it off). The guest agent then runs elevated, so Posato and the
     * bridge probe run through [runLimited] and [startLimited] with the signed-in user's filtered token, as a person's
     * Posato does.
     */
    val uacOn: Boolean by lazy {
        run(listOf("reg", "query", POLICIES_KEY, "/v", "EnableLUA")).stdout.lines().any { "EnableLUA" in it && it.trim().endsWith("0x1") }
    }

    /** Runs [command] as the signed-in user with the filtered token and returns its output and exit code. */
    fun runLimited(
        command: List<String>,
        stdin: ByteArray? = null,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): ProcessOutput {
        val arguments = listOf("-CommandLine", commandLine(command), "-TimeoutSeconds", timeout.seconds.toString()) +
            listOfNotNull(if (stdin != null) "-HasInput" else null)
        return run(POWERSHELL + listOf("-File", WindowsTools.LIMITED) + arguments, stdin, timeout.plusSeconds(LIMITED_SLACK_SECONDS))
    }

    /** Starts a cmd.exe [commandLine] as the signed-in user with the filtered token and returns without waiting. */
    fun startLimited(commandLine: String): ProcessOutput {
        return run(POWERSHELL + listOf("-File", WindowsTools.LIMITED, "-CommandLine", commandLine, "-NoWait"))
    }

    /** Runs a program in the guest directly, such as the probe's Python or a resource script. */
    fun run(
        command: List<String>,
        stdin: ByteArray? = null,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): ProcessOutput {
        val output = tart.exec(CLONE, command, stdin, timeout)
        return output.copy(stdout = output.stdout.replace("\r\n", "\n"), stderr = output.stderr.replace("\r\n", "\n"))
    }

    companion object {
        const val CLONE = "posato-run-windows"
        const val DEFAULT_GOLDEN = "posato-golden-windows"

        /** The golden VM's tools: JDK 21 with the Java Access Bridge and Python with java-access-bridge-wrapper. */
        const val TOOLS = "C:\\Tools"

        /** The guest sees the host folder `build/verification/windows-share/posato-sync` here, for a folder workspace. */
        private const val SHARE_NAME = "posato-sync"
        const val GUEST_FOLDER = "Z:\\$SHARE_NAME"

        fun syncShare(context: RunContext): Path = context.layout.verificationDirectory.resolve("windows-share").resolve(SHARE_NAME)

        val POWERSHELL = listOf("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass")
        private const val QUIET = "\$ProgressPreference = 'SilentlyContinue'"
        private const val MAX_ENCODED = 30_000
        private const val BOOT_TIMEOUT_MILLIS = 300_000L
        private const val PROBE_SECONDS = 15L
        private const val POLL_MILLIS = 2_000L
        private val DEFAULT_TIMEOUT: Duration = Duration.ofMinutes(5)

        private const val POLICIES_KEY = "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"
        private const val LIMITED_SLACK_SECONDS = 30L

        /** A cmd.exe command line: each argument with a space or nothing in it is quoted. */
        fun commandLine(command: List<String>): String = command.joinToString(" ") { if (it.isEmpty() || ' ' in it) "\"$it\"" else it }

        /** A PowerShell single-quoted string, in which only the quote itself needs doubling. */
        fun psQuote(text: String): String = "'" + text.replace("'", "''") + "'"
    }
}
