package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoEndTime
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoHero
import app.posato.prototype.designsystem.PosatoIntervalArtwork
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.SessionAction

@Composable
internal fun PrototypeSessionOverview(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = state.session.active
    val issues = state.reviewIssues().isNotEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHero(layout = layout, artworkContent = { PosatoIntervalArtwork() }, headingContent = {
            PrototypeOverviewHeading(state, layout)
        })
        if (active) PrototypeSessionEnd(state)
        if (issues && state.effectiveItemCount() > 0) PrototypeRepairNotice(state, onAction)
        if (active) {
            PosatoButton(onClick = { onAction(SessionAction.RequestEarlyEnd) }, style = PosatoButtonStyle.Quiet) { Text("End session early") }
        } else {
            val hasItems = state.effectiveItemCount() > 0
            PosatoButton(onClick = { onAction(if (hasItems) SessionAction.OpenSetup else ItemAction.OpenItems) }) {
                Text(if (hasItems) "Start a session" else "Choose paused items")
            }
        }
        PrototypeSelectedItems(state)
        PrototypeSyncStatus(state, onAction)
    }
}

@Composable
private fun PrototypeOverviewHeading(
    state: PrototypeState,
    layout: PosatoLayout,
    modifier: Modifier = Modifier
) {
    val active = state.session.active
    val issues = state.reviewIssues().isNotEmpty()
    val title = when {
        active && issues -> "Your pause needs a hand."
        active -> "A little room.\nJust for you."
        else -> "Room for what matters."
    }
    val description = when {
        active && issues -> "This session is still active, but some items may not be paused on this device."
        active -> "Your selected items are paused on this ${state.platform.label}."
        state.effectiveItemCount() == 0 -> "Start with a website or app you’d like a little space from."
        issues -> "Your choices are saved. A small setup step is needed before your next pause."
        else -> "A quiet pause is ready when you are. Choose a little space from the things that pull you away."
    }
    PosatoHeading(
        modifier = modifier,
        title = title,
        eyebrow = if (active) "SESSION ACTIVE" else "NO SESSION ACTIVE",
        description = description,
        layout = layout,
    )
}

@Composable
internal fun PrototypeSessionReview(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val issues = state.reviewIssues().isNotEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            if (issues) "A small step first." else "Your pause, your choice.",
            eyebrow = "ONE LAST LOOK",
            layout = layout,
            description = if (issues) {
                "Your selection is safe. Complete the setup below so this device can pause it."
            } else {
                "Here’s what will pause on this ${state.platform.label}. Other devices apply changes when they synchronize."
            },
        )
        PrototypeSessionEnd(state)
        if (issues) PrototypeRepairNotice(state, onAction)
        PosatoActionRow {
            PosatoButton(onClick = { onAction(SessionAction.Start) }, enabled = !issues) { Text("Start this pause") }
            PosatoButton(onClick = { onAction(SessionAction.OpenSetup) }, style = PosatoButtonStyle.Quiet) { Text("Change duration") }
        }
        PrototypeSelectedItems(state)
    }
}

@Composable
internal fun PrototypeSessionEnd(
    state: PrototypeState,
    modifier: Modifier = Modifier
) {
    PosatoEndTime(
        modifier = modifier.fillMaxWidth(),
        endTimeLabel = "Until ${state.session.endsAt.orEmpty()}",
        supportingText = "${state.session.durationMinutes} minutes · you stay in control",
    )
}
