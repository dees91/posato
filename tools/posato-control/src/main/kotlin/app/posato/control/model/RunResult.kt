package app.posato.control.model

import kotlinx.serialization.Serializable

@Serializable
data class StepError(
    val code: String,
    val message: String,
)

@Serializable
data class StepResult(
    val index: Int,
    val name: String? = null,
    val action: String,
    val ok: Boolean,
    val durationMs: Long,
    val artifacts: List<String> = emptyList(),
    val error: StepError? = null,
)

@Serializable
data class RunResult(
    val ok: Boolean,
    val steps: List<StepResult> = emptyList(),
    val error: StepError? = null,
)
