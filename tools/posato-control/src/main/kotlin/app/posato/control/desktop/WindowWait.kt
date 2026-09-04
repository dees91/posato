package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode

/** One poll of the addressed process: true once it owns a visible window, or a precondition refusal. */
fun interface WindowProbe {
    fun windowPresent(): Boolean
}

/**
 * Waits for the addressed process to own a visible window.
 *
 * A selected process is expected to be absent until the action that starts it takes effect, so only that refusal is
 * tolerated while waiting; every other precondition failure is reported at once. A selector that never resolves ends
 * as its own refusal rather than as a timeout, so a refusal is never mistaken for a window that failed to appear.
 */
object WindowWait {
    private const val MILLIS_PER_SECOND = 1000.0

    fun await(
        timeoutSeconds: Double,
        describe: String,
        clock: () -> Long = System::currentTimeMillis,
        sleep: (Long) -> Unit = Thread::sleep,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        probe: WindowProbe,
    ) {
        val deadline = clock() + (timeoutSeconds * MILLIS_PER_SECOND).toLong()
        var refusal: ControlException? = null
        while (true) {
            refusal = null
            val present = try {
                probe.windowPresent()
            } catch (exception: ControlException) {
                if (exception.code != ErrorCode.PROCESS_NOT_ALLOWED) throw exception
                refusal = exception
                false
            }
            if (present) return
            if (clock() >= deadline) {
                throw refusal ?: ControlException(
                    ErrorCode.WAIT_TIMEOUT,
                    "Timed out after $timeoutSeconds s waiting for a window of $describe.",
                    "Confirm the action that opens the window actually ran.",
                )
            }
            sleep(pollIntervalMs)
        }
    }

    const val DEFAULT_POLL_INTERVAL_MS: Long = 250L
}
