package app.posato.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.TimeSource

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

/** A system approval or password prompt may be open, so a back gesture must not leave the screen under it. */
internal fun MacSetupPresentation.promptInProgress(): Boolean {
    val waiting = setOf(MacSetupStepStatus.WAITING_FOR_APPROVAL, MacSetupStepStatus.WAITING_FOR_PASSWORD)
    val run = setup
    val runWaiting = run != null && (run.blocking in waiting || run.login in waiting || run.password in waiting)
    return runWaiting || activity == MacSetupActivity.ENABLING || activity == MacSetupActivity.REMOVING || standingGrantChanging
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
    val setupOpen: Boolean = false,
    val reads: Int = 0,
    val readyForSchedules: Boolean = false,
    val schedulesNeedConsent: Boolean = false,
)

@Stable
internal class MacHelperSetupUiState(
    private val macHelper: MacHelperPort,
    private val scope: CoroutineScope,
    private val sessionBusy: () -> Boolean = { false },
    private val loginContext: CoroutineContext = EmptyCoroutineContext,
    private val elapsedMillis: () -> Long = { processStart.elapsedNow().inWholeMilliseconds },
) {
    val loginItem: MacLoginItem? = macHelper.loginItem
    private val unfinishedReads = UnfinishedQuietReads(elapsedMillis)
    private var readinessState by mutableStateOf<MacHelperReadiness?>(null)

    /** Every stored answer ends a run of unfinished quiet reads. */
    var readiness: MacHelperReadiness?
        get() = readinessState
        private set(value) {
            readinessState = value
            unfinishedReads.reset()
        }
    var activity by mutableStateOf<MacSetupActivity?>(null)
        private set
    var removal by mutableStateOf<MacHelperRemoval?>(null)
        private set
    private var standingGrant by mutableStateOf<MacStandingGrantState?>(null)
    private var standingGrantChanging by mutableStateOf(false)
    private var quietRead = false
    private var reading = false
    private var lastRefresh: Long? = null
    private val trailingRead = TrailingRead(scope)
    private var reads by mutableIntStateOf(0)

    /** Whether a session is active or changing, fed by the host so every setup action can show it. */
    var sessionBlocked by mutableStateOf(false)
    private val runner = MacSetupRunner(macHelper, sessionBusy, loginContext)
    private val setupRun: MacSetupRun?
        get() {
            return runner.run
        }
    private var offerDismissed by mutableStateOf(macHelper.setupOfferDismissed())

    /** The consent to automatic starts as this Mac stores it; Schedules and the host read the same value. */
    val consent: MacConsentKeeper = MacConsentKeeper(macHelper.automaticStartConsent)
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
            setupOpen = setupOpen,
            reads = reads,
            readyForSchedules = macReadyForSchedules(complete, standingGrant, consent.given),
            schedulesNeedConsent = complete && standingGrant == MacStandingGrantState.ON && !consent.given,
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
        if (!enabled) {
            consent.record(false)
        }
        scope.launch {
            try {
                standingGrant = grant.setEnabled(enabled)
                consent.settle(standingGrant)
            } finally {
                standingGrantChanging = false
                // A quiet read that started before the change must not overwrite its answer.
                completedOperations += 1
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
        // The action's caption names automatic starts, including schedules added on other devices.
        consent.record(true)
        runner.start()
        scope.launch {
            var finished = false
            try {
                val outcome = try {
                    runner.execute()
                } finally {
                    runner.stop()
                }
                outcome.readiness?.let { answer -> readiness = answer }
                outcome.grant?.let { grant -> standingGrant = grant }
                completedOperations += 1
                finished = complete
                if (finished) {
                    dismissOffer()
                }
            } finally {
                // An unfinished run, including one cancelled with the window, withdraws the consent it recorded.
                if (!finished) {
                    consent.record(false)
                }
            }
        }
    }

    fun dismissOffer() {
        offerDismissed = true
        macHelper.dismissSetupOffer()
    }

    /** The person opened the setup screen; it stays until they leave it, even after a partial run. */
    var setupOpen by mutableStateOf(false)

    fun deferSetup() {
        runner.requestStop()
    }

    /**
     * Reads the actual state without progress or announcement. The first read happens once when Session
     * appears; [refresh] reads again when the window becomes active, so a setting changed in System
     * Settings never leaves a stale "ready".
     */
    fun readQuietly(
        refresh: Boolean = false,
        trailing: Boolean = false,
    ) {
        val alreadyRead = readiness != null || quietRead
        // Each read verifies the helper's signature, so activations share one read at a time and at most one per interval.
        val recentlyRefreshed = lastRefresh?.let { elapsedMillis() - it < REFRESH_INTERVAL_MILLIS } == true
        val inFlight = busy || reading
        val skipRefresh = !refresh || (recentlyRefreshed && !trailing)
        if (inFlight || (alreadyRead && skipRefresh)) {
            // A throttled return still reads once the interval ends, so a setting changed meanwhile is not missed.
            trailingRead.request(refresh && !trailing, lastRefresh, elapsedMillis()) { readQuietly(refresh = true, trailing = true) }
            return
        }
        quietRead = true
        reading = true
        lastRefresh = elapsedMillis()
        val startedAfter = completedOperations
        scope.launch {
            try {
                val answer = macHelper.status()
                val current = { !busy && completedOperations == startedAfter }
                if (current()) {
                    macHelper.loginItem?.refresh()
                    val grant = if (answer == MacHelperReadiness.READY) macHelper.standingGrant?.read() else null
                    // A setup run may have started while the grant was read; its answer wins.
                    // A kept unfinished read is read again after the refresh interval, so the run ends with a stored answer.
                    val stored = unfinishedReads.stores(current(), answer, readiness) {
                        val now = elapsedMillis()
                        trailingRead.request(true, now, now) { readQuietly(refresh = true, trailing = true) }
                    }
                    if (stored) {
                        readiness = answer
                        standingGrant = grant
                        consent.settle(answer)
                        consent.settle(grant)
                        reads += 1
                    }
                }
            } finally {
                reading = false
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
                consent.record(false)
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
                consent.settle(answer)
                consent.settle(standingGrant)
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
    val state = remember(macHelper) { MacHelperSetupUiState(macHelper, scope, { currentBusy() }, Dispatchers.Default) }
    LaunchedEffect(state) { state.consent.observe() }
    return state
}

/**
 * A quiet read whose outcome is unknown proves nothing about a helper last read as ready: on a busy
 * Mac launchd can take minutes to start the daemon, and the read then times out while blocking keeps
 * working. Ready stays while such reads last less than the grace period, and a retry after the
 * refresh interval reads again; a check or setup the person starts, and every other answer, still
 * show at once. A retry that is dropped, for example while another read runs, can leave the shown
 * ready until the next activation; that affects only what is shown, never enforcement. The clock is
 * monotonic and does not count sleep.
 */
private class UnfinishedQuietReads(
    private val elapsedMillis: () -> Long,
) {
    private var since: Long? = null

    fun reset() {
        since = null
    }

    /** Whether a current quiet read's [answer] replaces the [shown] readiness. */
    fun stores(
        current: Boolean,
        answer: MacHelperReadiness,
        shown: MacHelperReadiness?,
        retry: () -> Unit,
    ): Boolean {
        if (!current) {
            return false
        }
        if (answer != MacHelperReadiness.UNCERTAIN || shown != MacHelperReadiness.READY) {
            return true
        }
        val started = since ?: elapsedMillis().also { since = it }
        val expired = elapsedMillis() - started >= UNCERTAIN_READ_GRACE_MILLIS
        if (!expired) {
            retry()
        }
        return expired
    }
}

/** One deferred read at the end of the refresh interval, however many activations it absorbs. */
private class TrailingRead(
    private val scope: CoroutineScope,
) {
    private var pending = false

    fun request(
        wanted: Boolean,
        lastRefresh: Long?,
        now: Long,
        read: () -> Unit,
    ) {
        if (!wanted || pending) {
            return
        }
        pending = true
        val wait = (REFRESH_INTERVAL_MILLIS - (now - (lastRefresh ?: now))).coerceAtLeast(TRAILING_MINIMUM_MILLIS)
        scope.launch {
            delay(wait)
            pending = false
            read()
        }
    }
}

private const val REFRESH_INTERVAL_MILLIS: Long = 30_000L
private const val TRAILING_MINIMUM_MILLIS: Long = 1_000L
private const val UNCERTAIN_READ_GRACE_MILLIS: Long = 300_000L
private val processStart = TimeSource.Monotonic.markNow()

/** Keeps the consent to automatic starts in step with what this Mac stores and with the grant's actual answers. */
@Stable
internal class MacConsentKeeper(
    private val store: MacAutomaticStartConsent?,
) {
    var given by mutableStateOf(store?.given?.value == true)
        private set

    /** Follows the stored value, which the schedule host may clear while the window is closed. */
    suspend fun observe() {
        store?.given?.collect { given = it }
    }

    fun record(value: Boolean) {
        given = value
        store?.record(value)
    }

    /**
     * Records the consent from the Schedules card. It needs no password, because the grant is unchanged,
     * and it counts only on a Mac whose setup is verified while nothing else runs.
     */
    fun allow(presentation: MacSetupPresentation) {
        val idle = presentation.activity == null && presentation.setup?.running != true && !presentation.standingGrantChanging
        if (idle && presentation.schedulesNeedConsent) {
            record(true)
        }
    }

    /** An actual answer that the grant is off, unknown or unsupported withdraws the consent; an unread grant keeps it. */
    fun settle(grant: MacStandingGrantState?) {
        if (grant != null && grant != MacStandingGrantState.ON && given) {
            record(false)
        }
    }

    fun settle(readiness: MacHelperReadiness) {
        if (readiness == MacHelperReadiness.NOT_ENABLED && given) {
            record(false)
        }
    }
}
