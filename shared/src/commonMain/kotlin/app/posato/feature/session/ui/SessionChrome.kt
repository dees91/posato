package app.posato.feature.session.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.posato.core.designsystem.PosatoAlert
import app.posato.core.designsystem.PosatoAlertAction
import app.posato.core.designsystem.PosatoAlertRole
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarScreen
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoTypography
import app.posato.core.designsystem.PosatoWordmark

/**
 * Session's screens on iOS: the overview under a large title carrying the wordmark, with About in the bar, and the
 * steps of a new pause pushed with a bar back to the step before.
 */
@Composable
internal fun SessionBarScreen(
    route: SessionRoute,
    layout: PosatoLayout,
    onOpenAbout: (() -> Unit)?,
    onExitSetup: () -> Unit,
    onExitReview: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val latestContent by rememberUpdatedState(content)
    val body = remember { movableContentOf { scope: ColumnScope -> scope.latestContent() } }
    val padding = PaddingValues(horizontal = layout.screenInset)
    when (route) {
        SessionRoute.Overview -> PosatoBarScreen(
            title = "Session",
            largeTitle = true,
            largeTitleContent = { PosatoWordmark() },
            contentPadding = padding,
            trailingContent = {
                onOpenAbout?.let { open -> PosatoBarButton(onClick = open) { Text("About Posato", style = PosatoTypography.BarAction) } }
            },
        ) { body(this) }

        SessionRoute.Duration -> PosatoBarScreen(title = "New pause", backLabel = "Session", onBack = onExitSetup, contentPadding = padding) {
            body(this)
        }

        SessionRoute.Review -> PosatoBarScreen(title = "Review", backLabel = "New pause", onBack = onExitReview, contentPadding = padding) {
            body(this)
        }

        SessionRoute.MacSetup, SessionRoute.EarlyEnd, SessionRoute.ScheduledEarlyEnd -> PosatoBarScreen(
            title = "Session",
            contentPadding = padding,
        ) { body(this) }
    }
}

/**
 * Ending a pause early asks with the system alert on iOS. Keeping the pause is the bold, safe answer; ending it is
 * the destructive one.
 */
@Composable
internal fun SessionEarlyEndAlerts(
    state: SessionUiState,
    scheduled: ScheduledPauseView?,
    scheduledEnd: ScheduledEndActions,
    onConfirmEarlyEnd: () -> Unit,
    onCancelEarlyEnd: () -> Unit,
) {
    if (scheduled != null && scheduledEnd.confirming) {
        PosatoAlert(
            title = "Ready to return?",
            message = "This ends ${scheduled.name} and any session running now, here and on your other devices. The schedule keeps " +
                "repeating. A device whose date is different, in another time zone around midnight, keeps its own pause until it ends there.",
            onDismiss = scheduledEnd.onCancel,
            actions = listOf(
                PosatoAlertAction("Keep pausing", { scheduledEnd.onCancel() }, PosatoAlertRole.Cancel),
                PosatoAlertAction("End pause", { scheduledEnd.onConfirm() }, PosatoAlertRole.Destructive),
            ),
        )
    } else if (state.confirmingEarlyEnd) {
        PosatoAlert(
            title = "Ready to return?",
            message = if (state.nothingIsRestricted()) {
                "Nothing is restricted in this pause. You can end it now."
            } else {
                "You can end this session early. Your saved choices will stay ready for another time."
            },
            onDismiss = onCancelEarlyEnd,
            actions = listOf(
                PosatoAlertAction("Keep this pause", { onCancelEarlyEnd() }, PosatoAlertRole.Cancel),
                PosatoAlertAction("End session", { onConfirmEarlyEnd() }, PosatoAlertRole.Destructive),
            ),
        )
    }
}
