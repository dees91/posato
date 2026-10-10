package app.posato.control.linux

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import app.posato.control.desktop.AxBridgeBinary
import app.posato.control.vm.RecognizedLine
import app.posato.control.vm.Tart
import app.posato.control.vm.parseRecognizedLines
import app.posato.control.vm.shellQuote
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

/**
 * The Linux clone of ADR 0010's verification: a Tart Ubuntu guest with an X11 session, driven by screenshots taken in
 * the guest, text recognition on the host, and `xdotool` in the guest. Its desktop shows only Posato, so recognized
 * text locates controls the way a person reads them.
 */
class LinuxGuest(
    private val context: RunContext,
) {
    private val tart = Tart(context)
    private val golden = context.configuration.value(ConfigurationKey.VM_LINUX_GOLDEN) ?: DEFAULT_GOLDEN

    fun create(): String {
        if (tart.list().any { it.name == CLONE }) throw ControlException(ErrorCode.ALREADY_EXISTS, "$CLONE already exists; `linux destroy` first.")
        tart.clone(golden, CLONE)
        val log = context.layout.verificationDirectory.resolve("linux-run.log")
        context.subprocess.startDetached(listOf(Tart.TART, "run", CLONE, "--no-graphics"), log)
        val deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val probe = runCatching { tart.exec(CLONE, "test -S /tmp/.X11-unix/X0 && echo ready", timeout = Duration.ofSeconds(PROBE_SECONDS)) }
            if (probe.getOrNull()?.stdout?.contains("ready") == true) return CLONE
            Thread.sleep(POLL_MILLIS)
        }
        throw ControlException(ErrorCode.VM_UNAVAILABLE, "$CLONE did not reach its X11 session in time.")
    }

    fun destroy() {
        if (tart.list().none { it.name == CLONE }) return
        tart.stop(CLONE)
        tart.delete(CLONE)
    }

    val screen: LinuxScreen = LinuxScreen(context, this)

    fun exec(
        script: String,
        stdin: String? = null,
    ): ProcessOutput {
        // The guest agent's control socket sometimes refuses one connection; a person's retry is what the next attempt is.
        var attempt = 0
        while (true) {
            val output = tart.exec(CLONE, "export DISPLAY=:0 XAUTHORITY=\$HOME/.Xauthority; $script", stdin)
            if (AGENT_ERROR !in output.stderr || ++attempt >= AGENT_ATTEMPTS) return output
            Thread.sleep(AGENT_RETRY_MILLIS)
        }
    }

    /** Copies `build/linux/package` into the guest, packages it with jpackage there, and installs the `.deb` with apt. */
    fun install(packageDirectory: Path): String {
        if (!Files.isRegularFile(packageDirectory.resolve("posato.jar"))) {
            throw ControlException(ErrorCode.APP_NOT_STAGED, "No Linux package inputs; run ./gradlew :desktopApp:linuxPackageInputs.")
        }
        tart.pipe(
            CLONE,
            "COPYFILE_DISABLE=1 tar -C ${shellQuote(packageDirectory.toString())} -cf - .",
            "rm -rf ~/posato-package ~/posato-out && mkdir -p ~/posato-package ~/posato-out && tar -C ~/posato-package -xf - 2>/dev/null",
            "Copying the Linux package inputs into $CLONE",
        )
        val script = "sh ~/posato-package/package-deb.sh ~/posato-package $VERSION ~/posato-out && " +
            "sudo -n DEBIAN_FRONTEND=noninteractive apt-get install -y -q ~/posato-out/posato_${VERSION}_*.deb && ls ~/posato-out"
        return exec(script).requireSuccess(ErrorCode.INSTALL_FAILED, "Packaging and installing Posato in $CLONE").stdout.trim()
    }

    fun launch() {
        exec("pkill -x posato; sleep 1; nohup /opt/posato/bin/posato > /tmp/posato.log 2>&1 &")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Launching Posato in $CLONE")
    }

    fun terminate() {
        exec("pkill -x posato; true")
    }

    companion object {
        const val CLONE = "posato-run-linux"
        const val DEFAULT_GOLDEN = "posato-golden-linux"
        const val VERSION = "1.5.0"
        private const val BOOT_TIMEOUT_MILLIS = 180_000L
        private const val PROBE_SECONDS = 10L
        private const val POLL_MILLIS = 2_000L
        private const val AGENT_ERROR = "Tart Guest Agent"
        private const val AGENT_ATTEMPTS = 4
        private const val AGENT_RETRY_MILLIS = 1_500L
    }
}

/** The clone's screen: captured in the guest, read by text recognition on the host, and driven with `xdotool`. */
class LinuxScreen(
    private val context: RunContext,
    private val guest: LinuxGuest,
) {
    fun screenshot(file: Path): Path {
        val encoded = guest.exec("import -window root /tmp/posato-screen.png && base64 -w0 /tmp/posato-screen.png")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Capturing the screen of $CLONE").stdout.trim()
        Files.createDirectories(file.parent)
        Files.write(file, Base64.getDecoder().decode(encoded))
        return file
    }

    fun read(): List<RecognizedLine> {
        val frame = Files.createTempFile("posato-linux-screen", ".png")
        try {
            screenshot(frame)
            AxBridgeBinary(context).ensureBuilt()
            val output = context.subprocess.run(listOf(context.layout.accessibilityBridgeBinary.toString(), "ocr", frame.toString()))
                .requireSuccess(ErrorCode.COMMAND_FAILED, "Recognizing text on the Linux screen")
            return parseRecognizedLines(output.stdout)
        } finally {
            Files.deleteIfExists(frame)
        }
    }

    fun waitFor(
        text: String,
        exact: Boolean,
        timeoutMillis: Long,
    ): List<RecognizedLine> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val matches = read().filter { if (exact) it.text.trim() == text else it.text.contains(text, ignoreCase = true) }
            if (matches.isNotEmpty()) return matches
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(ErrorCode.WAIT_TIMEOUT, "\"$text\" did not appear on the Linux screen within ${timeoutMillis / MILLIS} s.")
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    fun click(line: RecognizedLine,) {
        guest.exec("xdotool mousemove ${line.centerX} ${line.centerY} click 1").requireSuccess(ErrorCode.COMMAND_FAILED, "Clicking in $CLONE")
    }

    fun type(text: String) {
        guest.exec("xdotool type --delay 30 -- ${shellQuote(text)}").requireSuccess(ErrorCode.COMMAND_FAILED, "Typing in $CLONE")
    }

    fun key(name: String) {
        guest.exec("xdotool key ${shellQuote(name)}").requireSuccess(ErrorCode.COMMAND_FAILED, "Pressing $name in $CLONE")
    }

    fun scroll(
        down: Boolean,
        clicks: Int,
    ) {
        val button = if (down) SCROLL_DOWN else SCROLL_UP
        guest.exec("xdotool mousemove 700 450 click --repeat $clicks $button").requireSuccess(ErrorCode.COMMAND_FAILED, "Scrolling in $CLONE")
    }

    private companion object {
        const val POLL_MILLIS = 2_000L
        const val MILLIS = 1_000
        const val SCROLL_DOWN = 5
        const val SCROLL_UP = 4
        const val CLONE = LinuxGuest.CLONE
    }
}
