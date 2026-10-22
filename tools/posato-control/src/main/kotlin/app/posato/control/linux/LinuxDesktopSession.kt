package app.posato.control.linux

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.vm.shellQuote

/** The Linux clone's desktop session: which session GDM logs the user in to, and a new login. */
class LinuxDesktopSession(
    private val guest: LinuxGuest,
) {
    /** Logs the session user out and in again through the display manager, as a new login does, and waits for it. */
    fun relogin() {
        guest.exec(
            "sudo -n systemctl restart display-manager",
        ).requireSuccess(ErrorCode.VM_UNAVAILABLE, "Restarting the display manager in ${LinuxGuest.CLONE}")
        val deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MILLIS
        Thread.sleep(POLL_MILLIS)
        while (System.currentTimeMillis() < deadline) {
            val state = guest.exec(
                "test -S /tmp/.X11-unix/X0 && loginctl show-session \$(loginctl list-sessions --no-legend | " +
                    "awk '\$3==\"'\$USER'\"{print \$1}' | tail -1) -p State --value",
            ).stdout.trim()
            if (state == "active") return
            Thread.sleep(POLL_MILLIS)
        }
        throw ControlException(ErrorCode.VM_UNAVAILABLE, "${LinuxGuest.CLONE} did not log the user in again in time.")
    }

    /**
     * Logs the GNOME line's user in again to [session]: GDM reads the user's session from AccountsService, which caches
     * it, so the display manager stops, the file and the daemon change, and GDM starts and logs the user in again.
     */
    fun selectSession(session: LinuxSession) {
        val script = "sudo -n sh -c " + shellQuote(
            "systemctl stop gdm3 && " +
                "sed -i 's/^DefaultSession=.*/DefaultSession=${session.desktop}.desktop/' /etc/gdm3/custom.conf && " +
                "sed -i 's/^Session=.*/Session=${session.desktop}/; s/^XSession=.*/XSession=${session.desktop}/' " +
                "/var/lib/AccountsService/users/\$SUDO_USER && " +
                "systemctl restart accounts-daemon && systemctl start gdm3",
        )
        guest.exec(script).requireSuccess(ErrorCode.VM_UNAVAILABLE, "Selecting the ${session.name.lowercase()} session in ${LinuxGuest.CLONE}")
        val deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val type = guest.exec(CURRENT_SESSION_TYPE)
            if (type.stdout.trim() == session.type && guest.exec("test -S /tmp/.X11-unix/X0").exitCode == 0) return
            Thread.sleep(POLL_MILLIS)
        }
        throw ControlException(ErrorCode.VM_UNAVAILABLE, "${LinuxGuest.CLONE} did not log in to the ${session.type} session in time.")
    }

    private companion object {
        private const val CURRENT_SESSION_TYPE = "loginctl show-session " +
            "\$(loginctl list-sessions --no-legend | awk '\$3==\"'\$USER'\"{print \$1}' | tail -1) -p Type --value"
        const val BOOT_TIMEOUT_MILLIS = 180_000L
        const val POLL_MILLIS = 2_000L
    }
}
