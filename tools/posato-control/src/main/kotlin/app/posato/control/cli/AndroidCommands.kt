package app.posato.control.cli

import app.posato.control.android.AndroidDevice
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Every `android` subcommand. Posato itself is driven with the shared commands and `-t android`. */
fun androidCommand(): AndroidCommand = AndroidCommand().subcommands(AndroidShellCommand())

class AndroidCommand : CliktCommand("android") {
    override fun help(context: Context): String = "Run a script on the Android device; drive Posato with the shared commands and -t android."

    override fun run() = Unit
}

class AndroidShellCommand : ControlCommand("shell", "Run an adb shell script on the device chosen by --udid or posato.android.serial.") {
    private val script by option("--script", help = "The script text.").required()

    override fun execute(session: Session): JsonElement {
        val device = AndroidDevice(session.context, session.options.udid)
        device.requireReady()
        val output = device.shell(script)
        return buildJsonObject {
            put("serial", device.serial)
            put("exitCode", output.exitCode)
            put("stdout", output.stdout)
            put("stderr", output.stderr)
        }
    }
}
