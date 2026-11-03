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
    /** iOS driver: when the step started, on the device clock. */
    val startedAtEpochMs: Long? = null,
    /** iOS `probeURL`: `blocked`, `open`, or `none`, and when the load started on the device clock. */
    val observed: String? = null,
    val observedAtEpochMs: Long? = null,
    /** iOS `probeURL`: the loads that showed nothing before the one reported in [observed]. */
    val probeRetries: Int? = null,
)

@Serializable
data class RunResult(
    val ok: Boolean,
    val steps: List<StepResult> = emptyList(),
    val error: StepError? = null,
)
