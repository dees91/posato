package app.posato.feature.sync.mailbox

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * How much bounded work one removal or sweep call may chain. Every caller
 * except an explicit workspace removal keeps [Capped], so the link's
 * fresh-attempt sweep stays bounded as ADR 0007 describes.
 */
internal sealed interface RemovalBudget {
    /** At most [MAX_PASSES] companion passes per call; the next call resumes. */
    data object Capped : RemovalBudget {
        const val MAX_PASSES: Int = 10
    }

    /**
     * One **Remove workspace** press: keep resuming while every checkpoint is
     * new within the call, end after [NO_PROGRESS_LIMIT] passes in a row
     * without progress or once [ceiling] has passed since the first pass.
     */
    data class WhileProgressing(
        val ceiling: Duration = DEFAULT_CEILING,
        val timeSource: TimeSource = TimeSource.Monotonic,
    ) : RemovalBudget

    companion object {
        const val NO_PROGRESS_LIMIT: Int = 3
        val DEFAULT_CEILING: Duration = 20.minutes
    }
}
