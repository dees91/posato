package app.posato.prototype.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoAppScaffold
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDeviceLabel
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoNavigationItem
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoWordmark
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.SessionAction

@Composable
internal fun PrototypeApplicationLayout(
    state: PrototypeState,
    brandFocus: FocusRequester,
    onOpenControls: () -> Unit,
    onAction: (PrototypeAction) -> Unit,
    onReviewDuration: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemBrowserState = rememberPrototypeItemBrowserState()
    PosatoAppScaffold(
        modifier = modifier.fillMaxSize(),
        navigationContent = { layout -> PrototypeNavigation(state, layout, brandFocus, onOpenControls, onAction) },
    ) { layout ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val contentModifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth()
            val contentPadding = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
            if (state.surface == PrototypeSurface.Items || state.surface == PrototypeSurface.Targets) {
                PrototypeItems(
                    modifier = contentModifier.padding(horizontal = contentPadding, vertical = PosatoSpace.Medium),
                    state = state,
                    layout = layout,
                    browser = itemBrowserState,
                    onAction = onAction,
                )
            } else {
                key(state.surface, state.editor.sessionKey) {
                    Column(
                        modifier = contentModifier.verticalScroll(rememberScrollState()).padding(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
                    ) {
                        PrototypeSurface(state, layout, onAction, onReviewDuration)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrototypeNavigation(
    state: PrototypeState,
    layout: PosatoLayout,
    brandFocus: FocusRequester,
    onOpenControls: () -> Unit,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = state.surface in setOf(PrototypeSurface.Items, PrototypeSurface.DomainEditor, PrototypeSurface.AppPicker)
    val padding = if (layout == PosatoLayout.Expanded) PosatoSpace.Section else PosatoSpace.Tiny
    val browsing = state.surface == PrototypeSurface.Items || state.surface == PrototypeSurface.Targets
    val compactInput = browsing && layout == PosatoLayout.Compact && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(modifier = modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
        if (!compactInput) {
            PosatoWordmark(
                Modifier.heightIn(min = PosatoSize.Control).focusRequester(brandFocus)
                    .combinedClickable(onClick = {}, onLongClickLabel = "Open prototype controls", onLongClick = onOpenControls)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Open prototype controls") {
                                onOpenControls()
                                true
                            },
                        )
                    },
            )
        }
        if (state.onboardingComplete) {
            PosatoActionRow {
                PosatoNavigationItem(!items, onClick = { onAction(SessionAction.ReturnToSession) }) { Text("Session") }
                PosatoNavigationItem(items, onClick = { onAction(ItemAction.OpenItems) }, enabled = !state.session.active) { Text("Paused items") }
            }
        } else {
            PosatoCaption("A quiet pause, in a few steps.")
        }
        if (layout == PosatoLayout.Expanded) PosatoDeviceLabel("On this ${state.platform.label}")
    }
}

@Composable
private fun PrototypeSurface(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    onReviewDuration: (String) -> Unit
) {
    when (state.surface) {
        PrototypeSurface.Welcome, PrototypeSurface.Privacy, PrototypeSurface.WorkspaceCheck,
        PrototypeSurface.KeyWait, PrototypeSurface.Permission -> PrototypeOnboarding(state, layout, onAction)

        PrototypeSurface.Targets, PrototypeSurface.Items -> Unit

        PrototypeSurface.DomainEditor -> PrototypeDomainEditor(state, layout, onAction)

        PrototypeSurface.AppPicker -> PrototypeApplicationPicker(state, layout, onAction)

        PrototypeSurface.SessionSetup -> PrototypeDuration(state, layout, onAction, onReviewDuration)

        PrototypeSurface.Home, PrototypeSurface.Active -> PrototypeSessionOverview(state, layout, onAction)

        PrototypeSurface.SessionReview -> PrototypeSessionReview(state, layout, onAction)

        PrototypeSurface.EarlyEnd, PrototypeSurface.Blocked, PrototypeSurface.Recovery -> PrototypeSessionInterruption(state, layout, onAction)
    }
}
