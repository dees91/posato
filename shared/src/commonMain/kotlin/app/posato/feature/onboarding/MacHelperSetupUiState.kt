package app.posato.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_setup_checking
import app.posato.generated.resources.mac_setup_enabling
import app.posato.generated.resources.mac_setup_removing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
    val sessionBlocked: Boolean = false,
)

@Stable
internal class MacHelperSetupUiState(
    private val macHelper: MacHelperPort,
    private val scope: CoroutineScope,
    private val sessionBusy: () -> Boolean = { false },
    private val loginContext: CoroutineContext = EmptyCoroutineContext,
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

    /** Whether a session is active or changing, fed by the host so every setup action can show it. */
    var sessionBlocked by mutableStateOf(false)
    private val runner = MacSetupRunner(macHelper, sessionBusy, loginContext)
    private val setupRun: MacSetupRun?
        get() {
            return runner.run
        }
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
            setupComplete = complete,
            offerVisible = !offerDismissed && upgradeOffered,
            sessionBlocked = sessionBlocked,
        )
    }

    private val complete: Boolean
        get() {
            return setupRun?.running != true &&
                macSetupComplete(readiness, macHelper.loginItem?.enabled?.value, macHelper.standingGrant != null, standingGrant)
        }

    /**
     * The upgrade offer appears only when a step is known to be missing. An unread or unknown grant
     * and an older daemon that cannot keep the permission never raise it. It stays through its own run.
     */
    private val upgradeOffered: Boolean
        get() {
            val grantUnknown = standingGrant == MacStandingGrantState.UNSUPPORTED || standingGrant == MacStandingGrantState.UNKNOWN
            return when {
                readiness != MacHelperReadiness.READY || grantUnknown -> false
                setupRun != null -> !complete
                else -> !busy && knownMissing(macHelper.loginItem?.enabled?.value, macHelper.standingGrant != null, standingGrant)
            }
        }

    /** Check, enable, remove, the grant switch, and setup share the helper, so only one runs at a time. */
    private val busy: Boolean
        get() {
            return activity != null || setupRun?.running == true || standingGrantChanging
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
        if (sessionBlocked || busy) {
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
     * One action makes this Mac ready: blocking, then opening at login, then starts without a
     * password. Finished steps are skipped, so pressing it again resumes the missing one. Nothing
     * that asks for approval or a password runs during a session.
     */
    fun setUp(sessionBlocked: Boolean) {
        if (sessionBlocked || sessionBusy() || busy) {
            return
        }
        removal = null
        runner.start()
        scope.launch {
            val outcome = try {
                runner.execute()
            } finally {
                runner.stop()
            }
            outcome.readiness?.let { answer -> readiness = answer }
            outcome.grant?.let { grant -> standingGrant = grant }
            completedOperations += 1
            if (complete) {
                dismissOffer()
            }
        }
    }

    fun dismissOffer() {
        offerDismissed = true
        macHelper.dismissSetupOffer()
    }

    /**
     * Session reads the helper once when it appears, so a ready Mac is never asked to finish setup. The
     * read shows no progress and makes no announcement; an explicit check or a running call wins.
     */
    fun readQuietly() {
        if (readiness != null || busy || quietRead) {
            return
        }
        quietRead = true
        val startedAfter = completedOperations
        scope.launch {
            val answer = macHelper.status()
            if (!busy && completedOperations == startedAfter) {
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
        if (sessionBlocked || busy) {
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
        if (busy) {
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
internal fun rememberMacHelperSetupUiState(
    macHelper: MacHelperPort,
    sessionBusy: () -> Boolean = { false },
): MacHelperSetupUiState {
    val scope = rememberCoroutineScope()
    val currentBusy by rememberUpdatedState(sessionBusy)
    return remember(macHelper) { MacHelperSetupUiState(macHelper, scope, { currentBusy() }, Dispatchers.Default) }
}
