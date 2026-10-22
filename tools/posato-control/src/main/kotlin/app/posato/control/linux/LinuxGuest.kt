package app.posato.control.linux

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import app.posato.control.vm.MachineLedger
import app.posato.control.vm.Tart
import app.posato.control.vm.shellQuote
import app.posato.control.vm.tartHome
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

/**
 * The Linux clone of ADR 0010's verification: a Tart Ubuntu guest with an X11 session. Posato's accessibility tree is
 * read through AT-SPI in the guest, and keys are typed with `xdotool`.
 */
class LinuxGuest(
    private val context: RunContext,
    line: LinuxLine = LinuxLine.OPENBOX,
) {
    private val tart = Tart(context)
    private val ledger = MachineLedger.of(context)
    private val golden = context.configuration.value(line.key) ?: line.defaultGolden

    fun create(): String {
        if (tart.list().any { it.name == CLONE }) throw ControlException(ErrorCode.ALREADY_EXISTS, "$CLONE already exists; `linux destroy` first.")
        ledger.claim(
            CLONE,
            tartHome().resolve("vms").resolve(CLONE),
            context.layout.root,
            belowReserve = false,
            memoryMib = tart.memoryMib(golden),
            command = "linux create",
        )
        try {
            tart.clone(golden, CLONE)
        } catch (failure: ControlException) {
            // No clone, so its lease would only hold back the reserve until it expires.
            ledger.release(CLONE)
            throw failure
        }
        return boot()
    }

    /** Starts the existing clone again after `stop`, keeping its installation and data. */
    fun boot(): String {
        val log = context.layout.verificationDirectory.resolve("linux-run.log")
        val share = syncShare(context)
        Files.createDirectories(share)
        share.toFile().setWritable(true, false)
        val command = listOf(Tart.TART, "run", CLONE, "--no-graphics", "--dir=$SHARE_NAME:$share")
        context.subprocess.startDetached(command, log)
        val deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val probe = runCatching { tart.exec(CLONE, "test -S /tmp/.X11-unix/X0 && echo ready", timeout = Duration.ofSeconds(PROBE_SECONDS)) }
            if (probe.getOrNull()?.stdout?.contains("ready") == true) {
                exec(MOUNT_SHARE).requireSuccess(ErrorCode.VM_UNAVAILABLE, "Mounting the synchronized folder in $CLONE")
                return CLONE
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw ControlException(ErrorCode.VM_UNAVAILABLE, "$CLONE did not reach its X11 session in time.")
    }

    fun stop() {
        tart.stop(CLONE)
    }

    /** Whether the clone exists and runs; every command in the guest needs it. */
    fun running(): Boolean = tart.list().any { it.name == CLONE && it.running }

    fun goldenExists(): Boolean = tart.list().any { it.name == golden }

    fun requireRunning() {
        if (!running()) {
            throw ControlException(
                ErrorCode.VM_UNAVAILABLE,
                "The Linux clone $CLONE is not running.",
                "Run `linux create` (or `linux boot`).",
            )
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

    val screen: LinuxScreen = LinuxScreen(this)

    /** The desktop session: GNOME's Xorg or Wayland choice, and logging the user in again. */
    val desktopSession: LinuxDesktopSession = LinuxDesktopSession(this)

    /** Posato started with its accessibility tree on AT-SPI, and the helper that reads and presses it. */
    val accessibility: LinuxAccessibility = LinuxAccessibility(this)

    fun exec(
        script: String,
        stdin: String? = null,
    ): ProcessOutput {
        // The guest agent's control socket sometimes refuses one connection; a person's retry is what the next attempt is.
        var attempt = 0
        while (true) {
            val output = tart.exec(CLONE, "$SESSION_ENVIRONMENT; $script", stdin)
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
            "sudo -n DEBIAN_FRONTEND=noninteractive apt-get install --reinstall -y -q ~/posato-out/posato_${VERSION}_*.deb && ls ~/posato-out"
        return exec(script).requireSuccess(ErrorCode.INSTALL_FAILED, "Packaging and installing Posato in $CLONE").stdout.trim()
    }

    /** Copies a built `.deb` into the clone and installs it with apt. */
    fun installPackage(file: Path): String {
        if (!Files.isRegularFile(file) || !file.fileName.toString().endsWith(".deb")) {
            throw ControlException(ErrorCode.APP_NOT_STAGED, "No .deb at $file.", "Pass a built posato_<version>_<arch>.deb.")
        }
        val name = file.fileName.toString()
        tart.pipe(
            CLONE,
            "cat ${shellQuote(file.toString())}",
            "rm -rf ~/posato-out && mkdir -p ~/posato-out && cat > ~/posato-out/${shellQuote(name)}",
            "Copying $name into $CLONE",
        )
        // The given file wins over whatever is installed, including the same or a newer version.
        val script = "sudo -n DEBIAN_FRONTEND=noninteractive apt-get install --reinstall --allow-downgrades -y -q " +
            "~/posato-out/${shellQuote(name)} && ls ~/posato-out"
        return exec(script).requireSuccess(ErrorCode.INSTALL_FAILED, "Installing $name in $CLONE").stdout.trim()
    }

    fun terminate() {
        exec("pkill -x posato; true")
    }

    companion object {
        const val CLONE = "posato-run-linux"

        /** Where Posato keeps its databases and choices in the guest (XDG data home of the test user). */
        const val DATA_DIRECTORY = "\$HOME/.local/share/Posato"

        /** The guest sees the host folder `build/verification/linux-sync` here; the relay serves it from the host. */
        const val GUEST_SHARE = "/mnt/shared"
        private const val SHARE_NAME = "posato-sync"
        private const val MOUNT_SHARE = "sudo -n mkdir -p $GUEST_SHARE && " +
            "(mountpoint -q $GUEST_SHARE || sudo -n mount -t virtiofs com.apple.virtio-fs.automount $GUEST_SHARE)"
        const val GUEST_FOLDER = "$GUEST_SHARE/$SHARE_NAME"

        fun syncShare(context: RunContext): Path = context.layout.verificationDirectory.resolve("linux-share").resolve(SHARE_NAME)

        const val DEFAULT_GOLDEN = "posato-golden-linux"
        const val DEFAULT_GNOME_GOLDEN = "posato-golden-linux-gnome"

        /** X11 under Xorg (GDM's or LightDM's authority in ~/.Xauthority) or under GNOME's Xwayland (mutter's file). */
        private const val SESSION_ENVIRONMENT = "export DISPLAY=:0 XAUTHORITY=\$HOME/.Xauthority; " +
            "for f in /run/user/\$(id -u)/.mutter-Xwaylandauth.*; do [ -e \"\$f\" ] && export XAUTHORITY=\"\$f\"; done"
        const val VERSION = "1.5.0"
        private const val BOOT_TIMEOUT_MILLIS = 180_000L
        private const val PROBE_SECONDS = 10L
        private const val POLL_MILLIS = 2_000L
        private const val AGENT_ERROR = "Tart Guest Agent"
        private const val AGENT_ATTEMPTS = 4
        private const val AGENT_RETRY_MILLIS = 1_500L
    }
}

/**
 * The golden a Linux clone starts from: the Openbox line for the shared commands, or the GNOME line (GDM, the
 * `ubuntu` session with its AppIndicator extension, Wayland by default and Xorg selectable) for the status area.
 */
enum class LinuxLine(
    val key: ConfigurationKey,
    val defaultGolden: String,
) {
    OPENBOX(ConfigurationKey.VM_LINUX_GOLDEN, LinuxGuest.DEFAULT_GOLDEN),
    GNOME(ConfigurationKey.VM_LINUX_GNOME_GOLDEN, LinuxGuest.DEFAULT_GNOME_GOLDEN),
}

/** The GNOME line's session: Xorg for `xdotool` and whole-screen capture, Wayland for what a stock Ubuntu runs. */
enum class LinuxSession(
    val desktop: String,
    val type: String,
) {
    XORG("ubuntu-xorg", "x11"),
    WAYLAND("ubuntu", "wayland"),
}

/** The clone's screen, captured in the guest, and its keyboard, driven with `xdotool`. */
class LinuxScreen(
    private val guest: LinuxGuest,
) {
    fun screenshot(file: Path): Path {
        // On Wayland only GNOME's own capture sees the whole screen; Xorg and Openbox use the X root window.
        val capture = "if [ \"\$(loginctl show-session \$XDG_SESSION_ID -p Type --value 2>/dev/null)\" = wayland ] || " +
            "[ -n \"\$(ls /run/user/\$(id -u)/.mutter-Xwaylandauth.* 2>/dev/null)\" ]; then " +
            "${LinuxAccessibility.SESSION_BUS}; gnome-screenshot -f /tmp/posato-screen.png; " +
            "else import -window root /tmp/posato-screen.png; fi"
        val encoded = guest.exec("$capture && base64 -w0 /tmp/posato-screen.png")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Capturing the screen of $CLONE").stdout.trim()
        Files.createDirectories(file.parent)
        Files.write(file, Base64.getDecoder().decode(encoded))
        return file
    }

    fun type(text: String) {
        guest.exec("xdotool type --delay 30 -- ${shellQuote(text)}").requireSuccess(ErrorCode.COMMAND_FAILED, "Typing in $CLONE")
    }

    fun key(name: String) {
        guest.exec("xdotool key ${shellQuote(name)}").requireSuccess(ErrorCode.COMMAND_FAILED, "Pressing $name in $CLONE")
    }

    private companion object {
        const val CLONE = LinuxGuest.CLONE
    }
}
