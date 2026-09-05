package app.posato.provisioning.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String,
    val hint: String? = null,
)

/**
 * The one shape every command prints.
 *
 * There is no run directory and no artifact list: a tool that holds an Apple credential writes nothing to disk that
 * it was not explicitly asked to install.
 */
@Serializable
data class Envelope(
    val ok: Boolean,
    val command: String,
    val durationMs: Long,
    val result: JsonElement? = null,
    val error: ErrorPayload? = null,
)
