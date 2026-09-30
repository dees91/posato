package app.posato.feature.sync.bootstrap

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

internal class ExchangeLoop(
    private val opportunities: ReceiveChannel<Unit>,
    private val backgroundTime: SyncBackgroundTime,
) {
    suspend fun run(exchange: suspend () -> SyncStatus) {
        var failures = 0
        var retryDelayMillis: Long? = null
        while (true) {
            if (awaitOpportunity(retryDelayMillis)) failures = 0
            val outcome = backgroundTime.begin().use { exchange() }
            if (outcome == SyncStatus.RETRYABLE) {
                retryDelayMillis = RETRY_DELAYS_MILLIS.getOrNull(failures)
                failures++
            } else {
                retryDelayMillis = null
                failures = 0
            }
        }
    }

    private suspend fun awaitOpportunity(retryDelayMillis: Long?): Boolean {
        if (retryDelayMillis == null) {
            opportunities.receive()
            return true
        }
        val hold = if (retryDelayMillis <= HELD_WAIT_LIMIT_MILLIS) backgroundTime else SyncBackgroundTime.None
        return hold.begin().use {
            withTimeoutOrNull(retryDelayMillis) { opportunities.receive() } != null
        }
    }
}

// A retryable pass retries on its own, then waits for a local change,
// foreground, Sync now, or the Mac's periodic exchange to start a new series.
private val RETRY_DELAYS_MILLIS = longArrayOf(5_000L, 15_000L, 60_000L, 300_000L, 900_000L)

// iOS grants a backgrounded app about 30 s in total, so only waits that can
// end inside that grant keep it awake.
private const val HELD_WAIT_LIMIT_MILLIS = 15_000L
