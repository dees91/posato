package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode

/** One poll of the addressed process: true once it owns a visible window, or a precondition refusal. */
fun interface WindowProbe {
    fun windowPresent(): Boolean
}

/** Which way a queryless `--process` wait is satisfied. */
enum class WindowState { PRESENT, ABSENT }

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
        state: WindowState = WindowState.PRESENT,
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
                // A selector that no longer resolves is the strongest form of an absent window: the process is gone.
                if (state == WindowState.ABSENT) return
                refusal = exception
                false
            }
            if (present == (state == WindowState.PRESENT)) return
            if (clock() >= deadline) {
                val expectation = if (state == WindowState.PRESENT) "a window of" else "the window of"
                val remedy = if (state == WindowState.PRESENT) {
                    "Confirm the action that opens the window actually ran."
                } else {
                    "Confirm the action that closes the window actually ran."
                }
                throw refusal ?: ControlException(
                    ErrorCode.WAIT_TIMEOUT,
                    "Timed out after $timeoutSeconds s waiting for $expectation $describe to " +
                        if (state == WindowState.PRESENT) "appear." else "close.",
                    remedy,
                )
            }
            sleep(pollIntervalMs)
        }
    }

    const val DEFAULT_POLL_INTERVAL_MS: Long = 250L
}
