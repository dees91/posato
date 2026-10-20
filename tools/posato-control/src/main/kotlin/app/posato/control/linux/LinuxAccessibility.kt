package app.posato.control.linux

import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.vm.shellQuote

/**
 * Posato's accessibility tree on AT-SPI in the Linux clone, which `-t linux` reads. Java reaches AT-SPI through the
 * guest's java-atk-wrapper; the packaged runtime looks for the wrapper's native library in its own directory, so the
 * library is linked there. Commands run outside the X session, so a session bus is started once and its address kept
 * in [BUS_FILE] for every later command. A program started on another session bus starts its own AT-SPI bus and
 * takes over the address the X session advertises, so every program a run starts in the guest uses this bus, and a
 * launch replaces any bus left over.
 */
class LinuxAccessibility(
    private val guest: LinuxGuest,
) {
    /** Starts Posato, ending a running one, with its tree published on AT-SPI. */
    fun launch() {
        guest.exec(ACCESSIBILITY_SESSION + "\n" + LINK_WRAPPER + "\npkill -x posato; sleep 1\n" + START_ACCESSIBLE)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Launching Posato in ${LinuxGuest.CLONE}")
    }

    /** Runs the AT-SPI helper in the guest: `dump`, `press <path>`, or `focus <path>`. */
    fun helper(vararg arguments: String): ProcessOutput {
        val script = checkNotNull(LinuxAccessibility::class.java.getResource(ATSPI_HELPER)).readText()
        val command = "export DBUS_SESSION_BUS_ADDRESS=\$(cat $BUS_FILE); python3 - ${arguments.joinToString(" ") { shellQuote(it) }}"
        return guest.exec(command, stdin = script)
    }

    companion object {
        const val BUS_FILE = "/tmp/posato-dbus"
        private const val ATSPI_HELPER = "/linux/posato-atspi.py"
        private const val ACCESSIBILITY_SESSION = "if ! DBUS_SESSION_BUS_ADDRESS=\$(cat $BUS_FILE 2>/dev/null) dbus-send --session " +
            "--dest=org.freedesktop.DBus / org.freedesktop.DBus.Peer.Ping >/dev/null 2>&1; then " +
            "dbus-launch | sed -n 's/^DBUS_SESSION_BUS_ADDRESS=//p' > $BUS_FILE; fi\n" +
            "export DBUS_SESSION_BUS_ADDRESS=\$(cat $BUS_FILE)\n" +
            // `pkill -f` would match this script's own shell; process names are cut to 15 characters.
            "pkill -x at-spi2-registr; pkill -x at-spi-bus-laun; sleep 1\n" +
            "(nohup /usr/libexec/at-spi-bus-launcher --launch-immediately >/tmp/atspi.log 2>&1 &)"
        private const val LINK_WRAPPER = "sudo -n ln -sf \$(find /usr/lib -name libatk-wrapper.so | head -1) " +
            "/opt/posato/lib/runtime/lib/libatk-wrapper.so"
        private const val START_ACCESSIBLE = "GNOME_ACCESSIBILITY=1 " +
            "JAVA_TOOL_OPTIONS='-Xbootclasspath/a:/usr/share/java/java-atk-wrapper.jar " +
            "-Djavax.accessibility.assistive_technologies=org.GNOME.Accessibility.AtkWrapper' " +
            "nohup /opt/posato/bin/posato > /tmp/posato.log 2>&1 &"
    }
}
