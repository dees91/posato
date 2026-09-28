package app.posato.control.cli

import app.posato.control.core.ControlJson
import app.posato.control.vm.GuestDialog
import app.posato.control.vm.GuestDialogs
import app.posato.control.vm.VmLine
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VmDialogsCommand :
    ControlCommand(
        "dialogs",
        "List the guest's open system dialogs by owning process: admin, gatekeeper, system-alert, accessibility, notification.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val dialogs = GuestDialogs(session.context).list(line)
        return buildJsonObject {
            put("vm", line.cloneName)
            put("dialogs", ControlJson.pretty.encodeToJsonElement(ListSerializer(GuestDialog.serializer()), dialogs))
        }
    }
}
