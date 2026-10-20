package app.posato.control.cli

import app.posato.control.linux.LinuxGuest
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
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
)

/** The Linux clone of ADR 0010: a Tart Ubuntu guest; Posato in it is driven with the shared commands and `-t linux`. */
class LinuxCommand : CliktCommand("linux") {
    override fun help(context: Context): String =
        "Create, start, stop, or delete the Tart Ubuntu clone, or run a script in it; drive Posato with -t linux."

    override fun run() = Unit
}

class LinuxCreateCommand : ControlCommand("create", "Clone the Linux golden VM and wait for its X11 session.") {
    override fun execute(session: Session): JsonElement = buildJsonObject { put("vm", LinuxGuest(session.context).create()) }
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
