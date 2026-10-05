package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.posato.core.designsystem.PosatoAlert
import app.posato.core.designsystem.PosatoAlertAction
import app.posato.core.designsystem.PosatoAlertRole
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarInset
import app.posato.core.designsystem.PosatoBarScreen
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTypography
import app.posato.core.designsystem.PosatoWordmark
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.targets.ui.TargetsCategory

/** The name a route's bar shows, which the screen pushed over it offers as the way back. */
internal fun SessionRoute.barTitle(): String {
    return when (this) {
        SessionRoute.Duration -> "New pause"
        SessionRoute.Review -> "Review"
        else -> "Session"
    }
}

/** A Session route's frame: [SessionBarScreen] on iOS, a scrolling column inset from the window elsewhere. */
@Composable
internal fun SessionRouteFrame(
    route: SessionRoute,
    layout: PosatoLayout,
    onOpenAbout: (() -> Unit)?,
    onExitSetup: () -> Unit,
    onExitReview: () -> Unit,
    backEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (platformUsesCupertinoChrome) {
        SessionBarScreen(route, onOpenAbout, onExitSetup, onExitReview, backEnabled, content)
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(layout.screenInset),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
            content = content,
        )
    }
}

/** The selected items pushed over [beneath], whose bar title leads back; it keeps its category while it slides away. */
@Composable
internal fun SessionItemsRoute(
    state: SessionUiState,
    category: TargetsCategory?,
    beneath: SessionRoute,
    onClose: () -> Unit,
    onEditItems: (TargetsCategory) -> Unit,
) {
    SessionSelectionScreen(
        state = state,
        initialCategory = category ?: TargetsCategory.WEBSITES,
        backLabel = rememberLastPresent(beneath.barTitle()).orEmpty(),
        inset = PosatoBarInset,
        onBack = onClose,
        onEditItems = onEditItems,
    )
}

/**
 * Session's screens on iOS: the overview under a large title carrying the wordmark, with About in the bar, unless
 * the iPad's sidebar carries both; and the steps of a new pause pushed with a bar back to the step before.
 */
@Composable
internal fun SessionBarScreen(
    route: SessionRoute,
    onOpenAbout: (() -> Unit)?,
    onExitSetup: () -> Unit,
    onExitReview: () -> Unit,
    backEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val latestContent by rememberUpdatedState(content)
    val body = remember { movableContentOf { scope: ColumnScope -> scope.latestContent() } }
    val padding = PaddingValues(horizontal = PosatoBarInset)
    when (route) {
        SessionRoute.Overview -> {
            PosatoBarScreen(
                title = route.barTitle(),
                largeTitle = true,
                // The app's identity, the wordmark and About, sits here unless a sidebar beside Session carries it.
                largeTitleContent = (@Composable { PosatoWordmark() }).takeIf { onOpenAbout != null },
                contentPadding = padding,
                trailingContent = {
                    // The system's info symbol keeps the bar light, so the title never truncates beside it at large sizes.
                    onOpenAbout?.let { open ->
                        PosatoBarButton(onClick = open, modifier = Modifier.semantics { contentDescription = "About Posato" }) {
                            PosatoIcon(PosatoIcons.Info, null, Modifier.size(PosatoSize.LargeIcon))
                        }
                    }
                },
            ) { body(this) }
        }

        SessionRoute.Duration -> {
            PosatoBarScreen(
                title = route.barTitle(),
                backLabel = SessionRoute.Overview.barTitle(),
                onBack = onExitSetup,
                backEnabled = backEnabled,
                contentPadding = padding,
            ) {
                body(this)
            }
        }

        SessionRoute.Review -> {
            PosatoBarScreen(
                title = route.barTitle(),
                backLabel = SessionRoute.Duration.barTitle(),
                onBack = onExitReview,
                backEnabled = backEnabled,
                contentPadding = padding,
            ) {
                body(this)
            }
        }

        SessionRoute.MacSetup, SessionRoute.EarlyEnd, SessionRoute.ScheduledEarlyEnd -> {
            PosatoBarScreen(
                title = route.barTitle(),
                contentPadding = padding,
            ) { body(this) }
        }

        // SessionScreen draws the pushed items screen itself.
        SessionRoute.Items -> {}
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
