package app.posato.control.cli

import app.posato.control.android.AndroidDevice
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Every `android` subcommand. */
fun androidCommand(): AndroidCommand = AndroidCommand().subcommands(
    AndroidInstallCommand(),
    AndroidLaunchCommand(),
    AndroidTerminateCommand(),
    AndroidResetCommand(),
    AndroidScreenshotCommand(),
    AndroidTextCommand(),
    AndroidWaitCommand(),
    AndroidTapCommand(),
    AndroidTypeCommand(),
    AndroidKeyCommand(),
    AndroidSwipeCommand(),
    AndroidShellCommand(),
)

/** Posato on an Android emulator (ADR 0010), driven with adb and UIAutomator; `--serial` overrides posato.android.serial. */
class AndroidCommand : CliktCommand("android") {
    override fun help(context: Context): String = "Install, launch, and drive Posato on an Android emulator."

    override fun run() = Unit
}

abstract class AndroidControlCommand(
    name: String,
    help: String,
) : ControlCommand(name, help) {
    private val serial by option("--serial", help = "adb serial of the emulator.")

    fun device(session: Session): AndroidDevice = AndroidDevice(session.context, serial)
}

class AndroidInstallCommand : AndroidControlCommand("install", "Install the debug APK and give it the grants a person gives in Settings.") {
    override fun execute(session: Session): JsonElement {
        val device = device(session)
        device.install(session.layout.root.resolve("androidApp/build/outputs/apk/debug/androidApp-debug.apk"))
        device.grant()
        return buildJsonObject { put("serial", device.serial) }
    }
}

class AndroidLaunchCommand : AndroidControlCommand("launch", "Open Posato.") {
    override fun execute(session: Session): JsonElement {
        device(session).launch()
        return buildJsonObject { put("launched", true) }
    }
}

class AndroidTerminateCommand : AndroidControlCommand("terminate", "Force-stop Posato.") {
    override fun execute(session: Session): JsonElement {
        device(session).terminate()
        return buildJsonObject { put("terminated", true) }
    }
}

class AndroidResetCommand : AndroidControlCommand("reset", "Clear Posato's data on the emulator.") {
    override fun execute(session: Session): JsonElement {
        device(session).reset()
        return buildJsonObject { put("reset", true) }
    }
}

class AndroidScreenshotCommand : AndroidControlCommand("screenshot", "Capture the screen into the run directory.") {
    private val name by option("--name", help = "File name without extension.").default("android-screen")

    override fun execute(session: Session): JsonElement {
        val file = device(session).screenshot(session.context.artifactPath("screenshots", "$name.png"))
        session.context.recordArtifact(file)
        return buildJsonObject { put("screenshot", file.toString()) }
    }
}

class AndroidTextCommand : AndroidControlCommand("text", "List the texts on screen with their positions.") {
    override fun execute(session: Session): JsonElement {
        val nodes = device(session).screen.nodes()
        return buildJsonObject {
            put(
                "lines",
                buildJsonArray {
                    nodes.forEach { node ->
                        add(
                            buildJsonObject {
                                put("text", node.text)
                                put("x", node.x)
                                put("y", node.y)
                            },
                        )
                    }
                },
            )
        }
    }
}

class AndroidWaitCommand : AndroidControlCommand("wait", "Wait until text appears on screen.") {
    private val text by option("--text", help = "Text to wait for.").required()
    private val exact by option("--exact", help = "Match the whole text.").flag()
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait.").long().default(ANDROID_WAIT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val found = device(session).screen.waitFor(text, exact, timeoutSeconds * ANDROID_MILLIS).first()
        return buildJsonObject { put("found", found.text) }
    }
}

class AndroidTapCommand : AndroidControlCommand("tap", "Tap the element with this text.") {
    private val text by option("--text", help = "Text to tap.").required()
    private val exact by option("--exact", help = "Match the whole text.").flag()
    private val index by option("--index", help = "Which match, top to bottom, from 0.").int().default(0)
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for it.").long().default(ANDROID_WAIT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val device = device(session).screen
        val node = device.waitFor(text, exact, timeoutSeconds * ANDROID_MILLIS).sortedBy {
            it.y
        }.getOrElse(index) { error("No match $index for $text") }
        device.tap(node)
        return buildJsonObject {
            put("tapped", node.text)
            put("x", node.x)
            put("y", node.y)
        }
    }
}

class AndroidTypeCommand : AndroidControlCommand("type", "Type text into the focused field.") {
    private val text by option("--text", help = "Text to type.").required()
    private val enter by option("--enter", help = "Press Enter afterwards.").flag()

    override fun execute(session: Session): JsonElement {
        val device = device(session).screen
        device.type(text)
        if (enter) device.key("KEYCODE_ENTER")
        return buildJsonObject { put("typed", text.length) }
    }
}

class AndroidKeyCommand : AndroidControlCommand("key", "Press a key, such as KEYCODE_BACK or KEYCODE_HOME.") {
    private val key by option("--key", help = "Android key code name.").required()

    override fun execute(session: Session): JsonElement {
        device(session).screen.key(key)
        return buildJsonObject { put("key", key) }
    }
}

class AndroidSwipeCommand : AndroidControlCommand("swipe", "Scroll the screen up by one swipe.") {
    override fun execute(session: Session): JsonElement {
        device(session).screen.swipeUp()
        return buildJsonObject { put("swiped", true) }
    }
}

class AndroidShellCommand : AndroidControlCommand("shell", "Run an adb shell script and print its output.") {
    private val script by option("--script", help = "The script text.").required()

    override fun execute(session: Session): JsonElement {
        val output = device(session).shell(script)
        return buildJsonObject {
            put("exitCode", output.exitCode)
            put("stdout", output.stdout)
            put("stderr", output.stderr)
        }
    }
}

private const val ANDROID_WAIT_SECONDS = 30L
private const val ANDROID_MILLIS = 1_000L
