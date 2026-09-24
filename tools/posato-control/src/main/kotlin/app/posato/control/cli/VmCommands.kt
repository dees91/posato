package app.posato.control.cli

import app.posato.control.vm.GuestPrompt
import app.posato.control.vm.VmLifecycle
import app.posato.control.vm.VmLine
import app.posato.control.vm.VmPrompts
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VmCommand : CliktCommand(name = "vm") {
    override fun help(context: Context): String =
        "Tart macOS guests for unattended desktop verification: create and destroy the per-run clone of a golden VM, " +
            "copy the staged package into it, and answer system dialogs over VNC. Desktop commands reach the guest with --vm primary|peer."

    override fun run() = Unit
}

class VmCreateCommand : ControlCommand("create", "Clone the line's golden VM, boot it headless, and copy the staged package and driver into it.") {
    private val lineOption by option("--line", help = "VM line: primary or peer.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement = VmLifecycle(session.context).create(VmLine.parse(lineOption))
}

class VmSyncCommand : ControlCommand("sync", "Copy the freshly staged package and the driver into the running clone.") {
    private val lineOption by option("--line", help = "VM line: primary or peer.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        VmLifecycle(session.context).sync(line)
        return buildJsonObject { put("vm", line.cloneName) }
    }
}

class VmDestroyCommand : ControlCommand("destroy", "Shut the clone down from inside the guest and delete it.") {
    private val lineOption by option("--line", help = "VM line: primary or peer.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        VmLifecycle(session.context).destroy(line)
        return buildJsonObject { put("vm", line.cloneName) }
    }
}

class VmPromptCommand : ControlCommand("prompt", "Answer a system dialog over VNC: admin, background, gatekeeper, or picker-bypass.") {
    private val lineOption by option("--line", help = "VM line: primary or peer.").default(VmLine.PRIMARY.id)
    private val kind by argument(
        help = "admin | background | toggle | account-password | mac-password | device-passcode | gatekeeper | picker-bypass",
    )
    private val row by option("--row", help = "toggle only: the System Settings row whose switch to turn on.")
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for the dialog.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val prompt = GuestPrompt.parse(kind)
        VmPrompts(session.context).answer(VmLine.parse(lineOption), prompt, timeoutSeconds * MILLIS_PER_SECOND, row)
        return buildJsonObject { put("prompt", prompt.id) }
    }
}

class VmClickCommand : ControlCommand("click", "Click text on the guest screen, located by text recognition.") {
    private val lineOption by option("--line", help = "VM line: primary or peer.").default(VmLine.PRIMARY.id)
    private val text by option("--text", help = "Text to find.").default("")
    private val exact by option("--exact", help = "Match the whole recognized line.").flag()
    private val index by option("--index", help = "The nth match, 0-based.").int().default(0)
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for the text.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        VmPrompts(session.context).click(VmLine.parse(lineOption), text, exact, index, timeoutSeconds * MILLIS_PER_SECOND)
        return buildJsonObject { put("clicked", text) }
    }
}

class VmPressCommand : ControlCommand("press", "Press a key or chord in the guest over VNC, such as return or cmd-q.") {
    private val lineOption by option("--line", help = "VM line: primary or peer.").default(VmLine.PRIMARY.id)
    private val key by argument(help = "Key or chord, for example return, escape, cmd-q.")

    override fun execute(session: Session): JsonElement {
        VmPrompts(session.context).press(VmLine.parse(lineOption), key)
        return buildJsonObject { put("pressed", key) }
    }
}

class VmScreenshotCommand : ControlCommand("screenshot", "Capture the whole guest screen over VNC into the run directory.") {
    private val lineOption by option("--line", help = "VM line: primary or peer.").default(VmLine.PRIMARY.id)
    private val name by option("--name", help = "Artifact name without extension.").default("guest-screen")

    override fun execute(session: Session): JsonElement {
        val path = VmPrompts(session.context).screenshot(VmLine.parse(lineOption), name)
        return buildJsonObject { put("path", session.layout.relativize(path)) }
    }
}

private const val DEFAULT_TIMEOUT_SECONDS = 60L
private const val MILLIS_PER_SECOND = 1_000L
