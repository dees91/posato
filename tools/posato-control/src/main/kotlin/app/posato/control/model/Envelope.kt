package app.posato.control.model

import app.posato.control.core.ControlJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String,
    val hint: String? = null,
)

@Serializable
data class Envelope(
    val ok: Boolean,
    val command: String,
    val target: String? = null,
    val runId: String,
    val durationMs: Long,
    val result: JsonElement? = null,
    val artifacts: List<String> = emptyList(),
    val error: ErrorPayload? = null,
)

/** The `--human` summary: the result, the artifacts, an error's hint, and the outcome last, where `| tail` shows it. */
fun Envelope.humanLines(): List<String> {
    return buildList {
        result?.let { result ->
            val text = (result as? JsonPrimitive)?.takeIf { it.isString }?.content
            add(text ?: ControlJson.pretty.encodeToString(JsonElement.serializer(), result))
        }
        artifacts.forEach { add("artifact: $it") }
        error?.hint?.let { add("hint: $it") }
        add(if (ok) "ok ($durationMs ms)" else "error ${error?.code}: ${error?.message}")
    }
}
