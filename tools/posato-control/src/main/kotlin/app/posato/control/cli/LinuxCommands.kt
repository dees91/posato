package app.posato.control.cli

import app.posato.control.linux.LinuxGuest
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Every `linux` subcommand. */
fun linuxCommand(): LinuxCommand = LinuxCommand().subcommands(
    LinuxCreateCommand(),
    LinuxDestroyCommand(),
    LinuxBootCommand(),
    LinuxStopCommand(),
    LinuxInstallCommand(),
    LinuxLaunchCommand(),
    LinuxTerminateCommand(),
    LinuxScreenshotCommand(),
    LinuxTextCommand(),
    LinuxWaitCommand(),
    LinuxClickCommand(),
    LinuxTypeCommand(),
    LinuxKeyCommand(),
    LinuxScrollCommand(),
    LinuxExecCommand(),
)

/** The Linux clone of ADR 0010: a Tart Ubuntu guest driven by screenshots, host text recognition, and `xdotool`. */
class LinuxCommand : CliktCommand("linux") {
    override fun help(context: Context): String = "Create, install, launch, and drive Posato in the Tart Ubuntu clone."

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

class LinuxInstallCommand : ControlCommand("install", "Package build/linux/package as a .deb in the clone and install it.") {
    override fun execute(session: Session): JsonElement {
        val inputs = session.layout.root.resolve("desktopApp/build/linux/package")
        return buildJsonObject { put("package", LinuxGuest(session.context).install(inputs)) }
    }
}

class LinuxLaunchCommand : ControlCommand("launch", "Start Posato in the clone's X11 session, ending a running one first.") {
    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).launch()
        return buildJsonObject { put("launched", true) }
    }
}

class LinuxTerminateCommand : ControlCommand("terminate", "End Posato in the clone.") {
    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).terminate()
        return buildJsonObject { put("terminated", true) }
    }
}

class LinuxScreenshotCommand : ControlCommand("screenshot", "Capture the clone's screen into the run directory.") {
    private val name by option("--name", help = "File name without extension.").default("linux-screen")

    override fun execute(session: Session): JsonElement {
        val file = LinuxGuest(session.context).screen.screenshot(session.context.artifactPath("screenshots", "$name.png"))
        session.context.recordArtifact(file)
        return buildJsonObject { put("screenshot", file.toString()) }
    }
}

class LinuxTextCommand : ControlCommand("text", "List the text recognized on the clone's screen with its positions.") {
    override fun execute(session: Session): JsonElement {
        val lines = LinuxGuest(session.context).screen.read()
        return buildJsonObject {
            put(
                "lines",
                buildJsonArray {
                    lines.forEach { line ->
                        add(
                            buildJsonObject {
                                put("text", line.text)
                                put("x", line.centerX)
                                put("y", line.centerY)
                            },
                        )
                    }
                },
            )
        }
    }
}

class LinuxWaitCommand : ControlCommand("wait", "Wait until text appears on the clone's screen.") {
    private val text by option("--text", help = "Text to wait for.").required()
    private val exact by option("--exact", help = "Match the whole recognized line.").flag()
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait.").long().default(DEFAULT_WAIT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val matches = LinuxGuest(session.context).screen.waitFor(text, exact, timeoutSeconds * MILLIS)
        return buildJsonObject { put("found", JsonPrimitive(matches.first().text)) }
    }
}

class LinuxClickCommand : ControlCommand("click", "Click recognized text on the clone's screen.") {
    private val text by option("--text", help = "Text to click.").required()
    private val exact by option("--exact", help = "Match the whole recognized line.").flag()
    private val index by option("--index", help = "Which match, top to bottom, from 0.").int().default(0)
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for it.").long().default(DEFAULT_WAIT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val guest = LinuxGuest(session.context).screen
        val match = guest.waitFor(text, exact, timeoutSeconds * MILLIS).sortedBy { it.y }.getOrElse(index) { error("No match $index for $text") }
        guest.click(match)
        return buildJsonObject {
            put("clicked", match.text)
            put("x", match.centerX)
            put("y", match.centerY)
        }
    }
}

class LinuxTypeCommand : ControlCommand("type", "Type text into the focused control of the clone.") {
    private val text by option("--text", help = "Text to type.").required()
    private val enter by option("--enter", help = "Press Return afterwards.").flag()

    override fun execute(session: Session): JsonElement {
        val guest = LinuxGuest(session.context).screen
        guest.type(text)
        if (enter) guest.key("Return")
        return buildJsonObject { put("typed", text.length) }
    }
}

class LinuxKeyCommand : ControlCommand("key", "Press a key in the clone, such as Return or ctrl+a.") {
    private val key by option("--key", help = "xdotool key name.").required()

    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).screen.key(key)
        return buildJsonObject { put("key", key) }
    }
}

class LinuxScrollCommand : ControlCommand("scroll", "Scroll the clone's window.") {
    private val up by option("--up", help = "Scroll up instead of down.").flag()
    private val clicks by option("--clicks", help = "Wheel clicks.").int().default(DEFAULT_SCROLL_CLICKS)

    override fun execute(session: Session): JsonElement {
        LinuxGuest(session.context).screen.scroll(down = !up, clicks = clicks)
        return buildJsonObject { put("scrolled", clicks) }
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

private const val DEFAULT_WAIT_SECONDS = 30L
private const val DEFAULT_SCROLL_CLICKS = 5
private const val MILLIS = 1_000L
