package app.posato.control.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
