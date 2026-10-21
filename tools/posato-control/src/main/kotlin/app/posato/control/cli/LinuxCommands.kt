package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.linux.LinuxGuest
import app.posato.control.linux.LinuxLine
import app.posato.control.linux.LinuxSession
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Every `linux` subcommand. */
fun linuxCommand(): LinuxCommand = LinuxCommand().subcommands(
    LinuxCreateCommand(),
    LinuxDestroyCommand(),
    LinuxBootCommand(),
    LinuxStopCommand(),
    LinuxExecCommand(),
    LinuxTrayCommand(),
    LinuxScreenCommand(),
    LinuxReloginCommand(),
    LinuxSessionCommand(),
)

/** The Linux clone of ADR 0010: a Tart Ubuntu guest; Posato in it is driven with the shared commands and `-t linux`. */
class LinuxCommand : CliktCommand("linux") {
    override fun help(context: Context): String =
        "Create, start, stop, or delete the Tart Ubuntu clone, or run a script in it; drive Posato with -t linux."

    override fun run() = Unit
}

class LinuxCreateCommand : ControlCommand("create", "Clone the Linux golden VM and wait for its X11 session.") {
    private val golden by option("--golden", help = "The golden line: openbox (default) or gnome (GDM, the ubuntu session).")
        .choice("openbox" to LinuxLine.OPENBOX, "gnome" to LinuxLine.GNOME)
        .default(LinuxLine.OPENBOX)

    private val desktopSession by option("--session", help = "With --golden gnome: xorg (default, the golden's own) or wayland.")
        .choice("xorg" to LinuxSession.XORG, "wayland" to LinuxSession.WAYLAND)

    override fun execute(session: Session): JsonElement {
        if (desktopSession != null && golden != LinuxLine.GNOME) throw UsageError("--session needs --golden gnome.")
        val guest = LinuxGuest(session.context, golden)
        val vm = guest.create()
        desktopSession?.takeIf { it != LinuxSession.XORG }?.let(guest.desktopSession::selectSession)
        return buildJsonObject {
            put("vm", vm)
            put("golden", golden.name.lowercase())
            desktopSession?.let { put("session", it.type) }
        }
    }
}

class LinuxBootCommand : ControlCommand("boot", "Start the stopped Linux clone again with its data.") {
    override fun execute(session: Session): JsonElement = buildJsonObject { put("vm", LinuxGuest(session.context).boot()) }
}

class LinuxStopCommand : ControlCommand("stop", "Stop the Linux clone and keep it.") {
    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).stop()
        return buildJsonObject { put("vm", LinuxGuest.CLONE) }
    }
}

class LinuxDestroyCommand : ControlCommand("destroy", "Stop and delete the Linux clone.") {
    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).destroy()
        return buildJsonObject { put("vm", LinuxGuest.CLONE) }
    }
}

class LinuxExecCommand : ControlCommand("exec", "Run a /bin/sh script in the clone's session and print its output.") {
    private val script by option("--script", help = "The script text.").required()

    override fun execute(session: Session): JsonElement {
        val output = LinuxGuest(session.context).exec(script)
        return buildJsonObject {
            put("exitCode", output.exitCode)
            put("stdout", output.stdout)
            put("stderr", output.stderr)
        }
    }
}

class LinuxTrayCommand : ControlCommand("tray", "Read Posato's status icon and menu over D-Bus, or open it, or click a menu item.") {
    private val activate by option("--activate", help = "Click the enabled menu item with this label.")
    private val open by option("--open", help = "Activate the icon, as a click on it does.").flag()

    override fun execute(session: Session): JsonElement {
        val guest = LinuxGuest(session.context).also { it.requireRunning() }
        val arguments = when {
            activate != null -> arrayOf("activate", activate!!)
            open -> arrayOf("open")
            else -> arrayOf("show")
        }
        val output = guest.accessibility.tray(*arguments)
        if (output.exitCode != 0) {
            throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "Posato's status icon: ${(output.stdout + output.stderr).trim()}")
        }
        return Json.parseToJsonElement(output.stdout.trim())
    }
}

class LinuxScreenCommand : ControlCommand("screen", "Capture the clone's whole screen, top bar and status area included.") {
    private val name by option("--name", help = "File name in the run's screenshots directory.").default("screen")

    override fun execute(session: Session): JsonElement {
        val guest = LinuxGuest(session.context).also { it.requireRunning() }
        val file = session.context.recordArtifact(guest.screen.screenshot(session.context.artifactPath("screenshots", "$name.png")))
        return buildJsonObject { put("screenshot", session.context.layout.relativize(file)) }
    }
}

class LinuxReloginCommand : ControlCommand("relogin", "Log the clone's user out and in again, so autostart entries run.") {
    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).also { it.requireRunning() }.desktopSession.relogin()
        return buildJsonObject { put("vm", LinuxGuest.CLONE) }
    }
}

class LinuxSessionCommand : ControlCommand("session", "On the GNOME line, log the user in to the Xorg or the Wayland session.") {
    private val desktopSession by argument(help = "xorg or wayland").choice("xorg" to LinuxSession.XORG, "wayland" to LinuxSession.WAYLAND)

    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).also { it.requireRunning() }.desktopSession.selectSession(desktopSession)
        return buildJsonObject { put("session", desktopSession.type) }
    }
}
