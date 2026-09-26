package app.posato.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_setup_checking
import app.posato.generated.resources.mac_setup_enabling
import app.posato.generated.resources.mac_setup_removing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

internal enum class MacSetupActivity {
    CHECKING,
    ENABLING,
    REMOVING,
}

internal fun MacSetupActivity.label(): StringResource {
    return when (this) {
        MacSetupActivity.CHECKING -> Res.string.mac_setup_checking
        MacSetupActivity.ENABLING -> Res.string.mac_setup_enabling
        MacSetupActivity.REMOVING -> Res.string.mac_setup_removing
    }
}

internal fun MacSetupPresentation.needsSetup(): Boolean {
    val known = readiness ?: return false
    return known != MacHelperReadiness.READY
}

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

internal data class MacSetupPresentation(
    val readiness: MacHelperReadiness? = null,
    val activity: MacSetupActivity? = null,
    val completedOperations: Long = 0,
    val repeatedResult: Boolean = false,
    val removal: MacHelperRemoval? = null,
    val standingGrant: MacStandingGrantState? = null,
    val standingGrantChanging: Boolean = false,
    val setup: MacSetupRun? = null,
    val setupComplete: Boolean = false,
    val offerVisible: Boolean = false,
)

@Stable
internal class MacHelperSetupUiState(
    private val macHelper: MacHelperPort,
    private val scope: CoroutineScope,
) {
    val loginItem: MacLoginItem? = macHelper.loginItem
    var readiness by mutableStateOf<MacHelperReadiness?>(null)
        private set
    var activity by mutableStateOf<MacSetupActivity?>(null)
        private set
    var removal by mutableStateOf<MacHelperRemoval?>(null)
        private set
    private var standingGrant by mutableStateOf<MacStandingGrantState?>(null)
    private var standingGrantChanging by mutableStateOf(false)
    private var quietRead = false
    private var setupRun by mutableStateOf<MacSetupRun?>(null)
    private var offerDismissed by mutableStateOf(macHelper.setupOfferDismissed())
    private var completedOperations by mutableLongStateOf(0)
    private var repeatedResult by mutableStateOf(false)

    /**
     * Answers already reported since the helper was last ready. A failing Enable alternates between
     * two answers rather than repeating one, so a consecutive-identical check would never see it.
     */
    private val reportedAnswers = mutableSetOf<MacHelperReadiness>()

    fun presentation(): MacSetupPresentation {
        return MacSetupPresentation(
            readiness = readiness,
            activity = activity,
            completedOperations = completedOperations,
            repeatedResult = repeatedResult,
            removal = removal,
            standingGrant = standingGrant,
            standingGrantChanging = standingGrantChanging,
            setup = setupRun,
            setupComplete = setupComplete(),
            offerVisible = readiness == MacHelperReadiness.READY && !setupComplete() && !offerDismissed && setupRun?.running != true,
        )
    }

    /**
     * The opt-in shares the helper with enforcement, so it is refused while a session is active or
     * changing: an administrator prompt would hold the helper and a lost reply would end it.
     */
    fun setStandingGrant(
        enabled: Boolean,
        sessionBlocked: Boolean,
    ) {
        val grant = macHelper.standingGrant ?: return
        if (sessionBlocked || activity != null || standingGrantChanging) {
            return
        }
        standingGrantChanging = true
        scope.launch {
            try {
                standingGrant = grant.setEnabled(enabled)
            } finally {
                standingGrantChanging = false
            }
        }
    }

    fun check() {
        run(MacSetupActivity.CHECKING, macHelper::recheck)
    }

    /**
     * Session reads the helper once when it appears, so a ready Mac is never asked to finish setup. The
     * read shows no progress and makes no announcement; an explicit check or a running call wins.
     */
    /**
     * One action makes this Mac ready: blocking, then opening at login, then starts without a
     * password. Finished steps are skipped, so pressing it again resumes the missing one. Nothing
     * that asks for approval or a password runs during a session.
     */
    fun setUp(sessionBlocked: Boolean) {
        if (sessionBlocked || activity != null || setupRun?.running == true) {
            return
        }
        removal = null
        setupRun = MacSetupRun(blocking = MacSetupStepStatus.WORKING, running = true)
        scope.launch {
            try {
                runSetup()
            } finally {
                setupRun = setupRun?.copy(running = false)
            }
        }
    }

    fun dismissOffer() {
        offerDismissed = true
        macHelper.dismissSetupOffer()
    }

    private suspend fun runSetup() {
        val blockingReady = makeBlockingReady()
        if (!blockingReady) {
            return
        }
        val item = macHelper.loginItem
        if (item != null && !item.enabled.value) {
            item.setEnabled(true)
        }
        val loginDone = item == null || item.enabled.value
        updateSetup { it.copy(login = if (loginDone) MacSetupStepStatus.DONE else MacSetupStepStatus.NEEDS_ATTENTION) }
        val grant = macHelper.standingGrant
        if (grant == null) {
            updateSetup { it.copy(password = MacSetupStepStatus.DONE) }
            return
        }
        var state = grant.read()
        if (!state.satisfiesSetup()) {
            updateSetup { it.copy(password = MacSetupStepStatus.WAITING_FOR_PASSWORD) }
            state = grant.setEnabled(true)
        }
        standingGrant = state
        updateSetup { it.copy(password = if (state.satisfiesSetup()) MacSetupStepStatus.DONE else MacSetupStepStatus.NEEDS_ATTENTION) }
    }

    private suspend fun makeBlockingReady(): Boolean {
        var answer = macHelper.recheck()
        if (answer == MacHelperReadiness.NOT_ENABLED || answer == MacHelperReadiness.UNAVAILABLE) {
            answer = macHelper.enable()
        }
        if (answer == MacHelperReadiness.APPROVAL_REQUIRED) {
            updateSetup { it.copy(blocking = MacSetupStepStatus.WAITING_FOR_APPROVAL) }
            macHelper.openApprovalSettings()
            var polls = 0
            while (answer == MacHelperReadiness.APPROVAL_REQUIRED && polls < APPROVAL_POLLS) {
                delay(APPROVAL_POLL_MILLIS)
                answer = macHelper.recheck()
                polls += 1
            }
        }
        readiness = answer
        completedOperations += 1
        val status = when (answer) {
            MacHelperReadiness.READY -> MacSetupStepStatus.DONE
            MacHelperReadiness.APPROVAL_REQUIRED -> MacSetupStepStatus.WAITING_FOR_APPROVAL
            else -> MacSetupStepStatus.NEEDS_ATTENTION
        }
        updateSetup { it.copy(blocking = status) }
        return answer == MacHelperReadiness.READY
    }

    private fun updateSetup(change: (MacSetupRun) -> MacSetupRun) {
        setupRun = change(setupRun ?: MacSetupRun())
    }

    private fun setupComplete(): Boolean {
        val loginDone = macHelper.loginItem?.enabled?.value ?: true
        val grantDone = macHelper.standingGrant == null || standingGrant?.satisfiesSetup() == true
        return readiness == MacHelperReadiness.READY && loginDone && grantDone
    }

    fun readQuietly() {
        if (readiness != null || activity != null || quietRead || setupRun?.running == true) {
            return
        }
        quietRead = true
        val startedAfter = completedOperations
        scope.launch {
            val answer = macHelper.status()
            if (activity == null && completedOperations == startedAfter && setupRun?.running != true) {
                macHelper.loginItem?.refresh()
                readiness = answer
                standingGrant = if (answer == MacHelperReadiness.READY) macHelper.standingGrant?.read() else null
            }
        }
    }

    fun enable() {
        run(MacSetupActivity.ENABLING, macHelper::enable)
    }

    fun openSettings() {
        macHelper.openApprovalSettings()
    }

    fun remove(sessionBlocked: Boolean) {
        if (sessionBlocked || activity != null) {
            return
        }
        activity = MacSetupActivity.REMOVING
        scope.launch {
            try {
                val result = macHelper.remove()
                removal = result
                standingGrant = null
                result.readinessAfterRemoval()?.let { next -> readiness = next }
                repeatedResult = false
                completedOperations += 1
            } finally {
                activity = null
            }
        }
    }

    private fun run(
        next: MacSetupActivity,
        action: suspend () -> MacHelperReadiness,
    ) {
        if (activity != null) {
            return
        }
        activity = next
        removal = null
        scope.launch {
            try {
                val answer = action()
                repeatedResult = answer != MacHelperReadiness.READY && !reportedAnswers.add(answer)
                if (answer == MacHelperReadiness.READY) {
                    reportedAnswers.clear()
                }
                standingGrant = if (answer == MacHelperReadiness.READY) macHelper.standingGrant?.read() else null
                readiness = answer
                completedOperations += 1
            } finally {
                activity = null
            }
        }
    }
}

private fun MacHelperRemoval.readinessAfterRemoval(): MacHelperReadiness? {
    return when (this) {
        MacHelperRemoval.REMOVED, MacHelperRemoval.NOT_ENABLED -> MacHelperReadiness.NOT_ENABLED
        MacHelperRemoval.APPROVAL_REQUIRED -> MacHelperReadiness.APPROVAL_REQUIRED
        MacHelperRemoval.CANNOT_START -> MacHelperReadiness.RECOVERY_REQUIRED
        MacHelperRemoval.REMOVE_AGAIN, MacHelperRemoval.UNCERTAIN, MacHelperRemoval.CHECK_AGAIN, MacHelperRemoval.PROXY_ATTENTION -> null
    }
}

@Composable
internal fun rememberMacHelperSetupUiState(macHelper: MacHelperPort): MacHelperSetupUiState {
    val scope = rememberCoroutineScope()
    return remember(macHelper) { MacHelperSetupUiState(macHelper, scope) }
}

private fun MacStandingGrantState.satisfiesSetup(): Boolean {
    return this == MacStandingGrantState.ON || this == MacStandingGrantState.UNSUPPORTED
}

private const val APPROVAL_POLLS: Int = 90
private const val APPROVAL_POLL_MILLIS: Long = 2_000L

