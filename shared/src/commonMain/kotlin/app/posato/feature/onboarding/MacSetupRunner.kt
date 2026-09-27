package app.posato.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal enum class MacSetupStepStatus {
    PENDING,
    WORKING,
    WAITING_FOR_APPROVAL,
    WAITING_FOR_PASSWORD,
    DONE,
    NEEDS_ATTENTION,
}

internal data class MacSetupRun(
    val blocking: MacSetupStepStatus = MacSetupStepStatus.PENDING,
    val login: MacSetupStepStatus = MacSetupStepStatus.PENDING,
    val password: MacSetupStepStatus = MacSetupStepStatus.PENDING,
    val running: Boolean = false,
)

/** What a run verified; a null field was not read and leaves the holder's value alone. */
internal data class MacSetupOutcome(
    val readiness: MacHelperReadiness? = null,
    val grant: MacStandingGrantState? = null,
)

/** Complete means blocking works, the login item is on, and starts without a password are allowed. */
internal fun macSetupComplete(
    readiness: MacHelperReadiness?,
    loginOn: Boolean?,
    grantSupported: Boolean,
    grant: MacStandingGrantState?,
): Boolean {
    return readiness == MacHelperReadiness.READY && loginOn != false && (!grantSupported || grant == MacStandingGrantState.ON)
}

internal fun knownMissing(
    loginOn: Boolean?,
    grantSupported: Boolean,
    grant: MacStandingGrantState?,
): Boolean {
    return loginOn == false || (grantSupported && grant == MacStandingGrantState.OFF)
}

/**
 * One action makes this Mac ready: blocking, then opening at login, then starts without a password.
 * Finished steps are skipped, so running it again resumes the missing one. A session that starts
 * mid-run stops it before anything that asks for approval or a password.
 */
internal class MacSetupRunner(
    private val macHelper: MacHelperPort,
    private val sessionBusy: () -> Boolean,
    private val loginContext: CoroutineContext,
) {
    var run by mutableStateOf<MacSetupRun?>(null)
        private set

    fun start() {
        run = MacSetupRun(blocking = MacSetupStepStatus.WORKING, running = true)
    }

    fun stop() {
        run = run?.copy(running = false)
    }

    suspend fun execute(): MacSetupOutcome {
        val readiness = blocking()
        if (readiness != MacHelperReadiness.READY) {
            return MacSetupOutcome(readiness = readiness)
        }
        val item = macHelper.loginItem
        if (item != null && !item.enabled.value) {
            withContext(loginContext) { item.setEnabled(true) }
        }
        val loginDone = item == null || item.enabled.value
        update { it.copy(login = if (loginDone) MacSetupStepStatus.DONE else MacSetupStepStatus.NEEDS_ATTENTION) }
        val grant = macHelper.standingGrant
        if (grant == null) {
            update { it.copy(password = MacSetupStepStatus.DONE) }
            return MacSetupOutcome(readiness = readiness)
        }
        var state = grant.read()
        if (state != MacStandingGrantState.ON && state != MacStandingGrantState.UNSUPPORTED && !sessionBusy()) {
            update { it.copy(password = MacSetupStepStatus.WAITING_FOR_PASSWORD) }
            state = grant.setEnabled(true)
        }
        update { it.copy(password = if (state == MacStandingGrantState.ON) MacSetupStepStatus.DONE else MacSetupStepStatus.NEEDS_ATTENTION) }
        return MacSetupOutcome(readiness = readiness, grant = state)
    }

    private suspend fun blocking(): MacHelperReadiness {
        var answer = macHelper.recheck()
        if ((answer == MacHelperReadiness.NOT_ENABLED || answer == MacHelperReadiness.UNAVAILABLE) && !sessionBusy()) {
            answer = macHelper.enable()
        }
        if (answer == MacHelperReadiness.APPROVAL_REQUIRED) {
            update { it.copy(blocking = MacSetupStepStatus.WAITING_FOR_APPROVAL) }
            macHelper.openApprovalSettings()
            var polls = 0
            while (answer == MacHelperReadiness.APPROVAL_REQUIRED && polls < APPROVAL_POLLS) {
                delay(APPROVAL_POLL_MILLIS)
                answer = macHelper.recheck()
                polls += 1
            }
        }
        val status = when (answer) {
            MacHelperReadiness.READY -> MacSetupStepStatus.DONE
            MacHelperReadiness.APPROVAL_REQUIRED -> MacSetupStepStatus.WAITING_FOR_APPROVAL
            else -> MacSetupStepStatus.NEEDS_ATTENTION
        }
        update { it.copy(blocking = status) }
        return answer
    }

    private fun update(change: (MacSetupRun) -> MacSetupRun) {
        run = change(run ?: MacSetupRun())
    }
}

private const val APPROVAL_POLLS: Int = 90
private const val APPROVAL_POLL_MILLIS: Long = 2_000L
