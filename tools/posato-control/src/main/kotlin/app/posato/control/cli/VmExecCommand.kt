package app.posato.control.cli

import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VmExecCommand :
    ControlCommand(
        "exec",
        "Run a /bin/sh script as the logged-in user in the guest and print its exit code and output.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val script by option("--script", help = "The /bin/sh script to run in the guest.").required()

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val output = Tart(session.context).exec(line.cloneName, script)
        return buildJsonObject {
            put("vm", line.cloneName)
            put("exitCode", output.exitCode)
            put("stdout", output.stdout)
            put("stderr", output.stderr)
        }
    }
}
