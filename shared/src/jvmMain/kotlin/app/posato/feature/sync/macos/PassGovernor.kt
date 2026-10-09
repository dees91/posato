package app.posato.feature.sync.macos

import app.posato.feature.sync.mailbox.RemovalBudget
import java.security.MessageDigest
import kotlin.time.TimeMark

/**
 * Decides whether one removal or sweep call may run another companion pass.
 * [RemovalBudget.Capped] allows a fixed number of passes. Under
 * [RemovalBudget.WhileProgressing] a pass counts as progress only when its
 * checkpoint was not seen before in this call, so a stalled or looping
 * cursor ends the call; the ceiling bounds a cursor that keeps advancing.
 * Only digests of checkpoints are kept, never the opaque tokens themselves.
 */
internal class PassGovernor(
    private val budget: RemovalBudget,
) {
    private var passes = 0
    private var passesWithoutProgress = 0
    private var started: TimeMark? = null
    private val seen = mutableSetOf<String>()

    fun mayRun(): Boolean {
        val allowed = when (budget) {
            RemovalBudget.Capped -> {
                passes < RemovalBudget.Capped.MAX_PASSES
            }

            is RemovalBudget.WhileProgressing -> {
                val mark = started ?: budget.timeSource.markNow().also { started = it }
                passesWithoutProgress < RemovalBudget.NO_PROGRESS_LIMIT && mark.elapsedNow() < budget.ceiling
            }
        }
        if (allowed) passes += 1
        return allowed
    }

    fun recordCheckpoint(cursor: ByteArray) {
        if (seen.add(digest(cursor))) passesWithoutProgress = 0 else passesWithoutProgress += 1
    }

    fun recordNoProgress() {
        passesWithoutProgress += 1
    }

    private fun digest(cursor: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(cursor).joinToString("") { "%02x".format(it) }
    }
}
