package app.posato.control.cli

import app.posato.control.core.ControlJson
import app.posato.control.vm.GuestText
import app.posato.control.vm.RecognizedLine
import app.posato.control.vm.VmLine
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VmTextCommand :
    ControlCommand(
        "text",
        "Print the text recognized on the guest screen, top to bottom, instead of a screenshot.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val contains by option("--contains", help = "Keep only lines that contain this text (case-insensitive).")

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        return linesJson(line, GuestText(session.context).read(line, contains).sortedWith(compareBy({ it.y }, { it.x })))
    }
}

class VmWaitTextCommand :
    ControlCommand(
        "wait-text",
        "Wait until text appears on the guest screen (or disappears with --absent), polling text recognition.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val text by option("--text", help = "Text to wait for; a line containing it matches.").required()
    private val exact by option("--exact", help = "Match only a line equal to the text.").flag()
    private val absent by option("--absent", help = "Wait until no line contains the text.").flag()
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait.").long().default(WAIT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val matches = GuestText(session.context).waitFor(line, text, exact, absent, timeoutSeconds * MILLIS_PER_SECOND)
        return linesJson(line, matches)
    }
}

private fun linesJson(
    line: VmLine,
    lines: List<RecognizedLine>
): JsonElement = buildJsonObject {
    put("vm", line.cloneName)
    put("lines", ControlJson.pretty.encodeToJsonElement(ListSerializer(RecognizedLine.serializer()), lines))
}

private const val WAIT_TIMEOUT_SECONDS = 60L
private const val MILLIS_PER_SECOND = 1_000L
