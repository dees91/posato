package app.posato.control.linux

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.vm.shellQuote

/**
 * Posato's accessibility tree on AT-SPI in the Linux clone, which `-t linux` reads. Java reaches AT-SPI through the
 * guest's java-atk-wrapper; the packaged runtime looks for the wrapper's native library in its own directory, so the
 * library is linked there. Commands run outside the X session, so a session bus is started once and its address kept
 * in [BUS_FILE] for every later command. A program started on another session bus starts its own AT-SPI bus and
 * takes over the address the X session advertises, so every program a run starts in the guest uses this bus, and a
 * launch replaces any bus left over. In a GNOME session (the GNOME golden line) [BUS_FILE] holds the session's own
 * bus instead, so Posato's status icon reaches the shell's watcher and the shell's AT-SPI bus stays in use, with
 * toolkit accessibility turned on.
 */
class LinuxAccessibility(
    private val guest: LinuxGuest,
) {
    /**
     * Starts Posato, ending a running one, and returns once its tree can be read on AT-SPI. The process starts at
     * once but takes seconds to register, and a command that reads the tree before then fails.
     */
    fun launch() {
        guest.exec(ACCESSIBILITY_SESSION + "\n" + LINK_WRAPPER + "\npkill -x posato; sleep 1\n" + START_ACCESSIBLE)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Launching Posato in ${LinuxGuest.CLONE}")
        val deadline = System.currentTimeMillis() + REGISTER_MILLIS
        while (!helper("dump").succeeded) {
            if (System.currentTimeMillis() > deadline) {
                throw ControlException(
                    ErrorCode.COMMAND_FAILED,
                    "Posato did not appear on the accessibility bus within ${REGISTER_MILLIS / MILLIS_PER_SECOND} s.",
                    "Read /tmp/posato.log in the clone with `linux exec`.",
                )
            }
            Thread.sleep(REGISTER_POLL_MILLIS)
        }
    }

    /** Runs the status-icon helper in the guest on the session's bus: `show`, `open`, or `activate <label>`. */
    fun tray(vararg arguments: String): ProcessOutput {
        val script = checkNotNull(LinuxAccessibility::class.java.getResource(TRAY_HELPER)).readText()
        val command = "$SESSION_BUS; python3 - ${arguments.joinToString(" ") { shellQuote(it) }}"
        return guest.exec(command, stdin = script)
    }

    /** Runs the AT-SPI helper in the guest: `dump`, `press <path>`, or `focus <path>`. */
    fun helper(vararg arguments: String): ProcessOutput {
        val script = checkNotNull(LinuxAccessibility::class.java.getResource(ATSPI_HELPER)).readText()
        val command = "export DBUS_SESSION_BUS_ADDRESS=\$(cat $BUS_FILE); python3 - ${arguments.joinToString(" ") { shellQuote(it) }}"
        return guest.exec(command, stdin = script)
    }

    companion object {
        const val BUS_FILE = "/tmp/posato-dbus"
        private const val REGISTER_MILLIS = 60_000L
        private const val REGISTER_POLL_MILLIS = 500L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val ATSPI_HELPER = "/linux/posato-atspi.py"
        private const val TRAY_HELPER = "/linux/posato-tray.py"

        /** The logged-in session's own bus, which the desktop shell and its status-icon watcher use. */
        const val SESSION_BUS = "export XDG_RUNTIME_DIR=/run/user/\$(id -u) DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/\$(id -u)/bus"
        private const val PRIVATE_SESSION = "if ! DBUS_SESSION_BUS_ADDRESS=\$(cat $BUS_FILE 2>/dev/null) dbus-send --session " +
            "--dest=org.freedesktop.DBus / org.freedesktop.DBus.Peer.Ping >/dev/null 2>&1; then " +
            "dbus-launch | sed -n 's/^DBUS_SESSION_BUS_ADDRESS=//p' > $BUS_FILE; fi\n" +
            "export DBUS_SESSION_BUS_ADDRESS=\$(cat $BUS_FILE)\n" +
            // `pkill -f` would match this script's own shell; process names are cut to 15 characters.
            "pkill -x at-spi2-registr; pkill -x at-spi-bus-laun; sleep 1\n" +
            "(nohup /usr/libexec/at-spi-bus-launcher --launch-immediately >/tmp/atspi.log 2>&1 &)"
        private const val ACCESSIBILITY_SESSION = "if pgrep -u \$(id -u) -x gnome-shell >/dev/null; then " +
            "$SESSION_BUS; echo \$DBUS_SESSION_BUS_ADDRESS > $BUS_FILE; " +
            "gsettings set org.gnome.desktop.interface toolkit-accessibility true; else\n" + PRIVATE_SESSION + "\nfi"
        private const val LINK_WRAPPER = "sudo -n ln -sf \$(find /usr/lib -name libatk-wrapper.so | head -1) " +
            "/opt/posato/lib/runtime/lib/libatk-wrapper.so"
        private const val START_ACCESSIBLE = "GNOME_ACCESSIBILITY=1 " +
            "JAVA_TOOL_OPTIONS='-Xbootclasspath/a:/usr/share/java/java-atk-wrapper.jar " +
            "-Djavax.accessibility.assistive_technologies=org.GNOME.Accessibility.AtkWrapper' " +
            "nohup /opt/posato/bin/posato > /tmp/posato.log 2>&1 &"
    }
}
