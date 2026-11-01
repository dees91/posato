package app.posato.feature.session.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.feature.presence.SessionWindowRequest
import app.posato.feature.schedules.host.ScheduledBreaks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Take a break asks for a length; a manual session in the workspace makes it unavailable, also at the press. */
@Stable
internal class ScheduledBreakState(
    private val breaks: () -> ScheduledBreaks?,
    private val manualActive: () -> Boolean,
    private val scope: CoroutineScope,
) {
    var choosing by mutableStateOf(false)

    fun actions(running: Boolean): ScheduledBreakActions {
        return ScheduledBreakActions(
            choosing = choosing && running,
            onRequest = { choosing = true },
            onChoose = { minutes ->
                choosing = false
                if (!manualActive()) {
                    scope.launch { breaks()?.start(minutes) }
                }
            },
            onCancel = { choosing = false },
            onEnd = { scope.launch { breaks()?.end() } },
        )
    }
}

/** The menu's request opens setup or the End early confirmation once Session has loaded. */
@Composable
internal fun WindowRequestEffect(
    windowRequest: SessionWindowRequest?,
    awaitLoaded: suspend () -> Unit,
    onStartSession: () -> Unit,
    onConsume: () -> Unit,
    onEndEarly: () -> Unit,
) {
    val loaded by rememberUpdatedState(awaitLoaded)
    val consume by rememberUpdatedState(onConsume)
    val startSession by rememberUpdatedState(onStartSession)
    val endEarly by rememberUpdatedState(onEndEarly)
    LaunchedEffect(windowRequest) {
        if (windowRequest == null) return@LaunchedEffect
        loaded()
        when (windowRequest) {
            SessionWindowRequest.START_SESSION -> {
                startSession()
            }

            SessionWindowRequest.END_SESSION_EARLY -> {
                endEarly()
            }

            SessionWindowRequest.SESSION -> {}
        }
        consume()
    }
}

/** The early-end alerts on iOS, where confirmations are alerts, and the break length choice everywhere. */
@Composable
internal fun SessionAlerts(
    state: SessionUiState,
    scheduled: ScheduledPauseView?,
    scheduledEnd: ScheduledEndActions,
    scheduledBreak: ScheduledBreakActions,
    onConfirmEarlyEnd: () -> Unit,
    onCancelEarlyEnd: () -> Unit,
) {
    if (platformUsesCupertinoChrome) {
        SessionEarlyEndAlerts(state, scheduled, scheduledEnd, onConfirmEarlyEnd, onCancelEarlyEnd)
    }
    scheduled?.let { ScheduledBreakAlert(it, scheduledBreak) }
}
