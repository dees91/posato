package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoNavigationPlacement
import app.posato.prototype.designsystem.PosatoNavigationScaffold
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSurface

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
    val placement = if (state.platform == PrototypePlatform.IPhone) PosatoNavigationPlacement.Bottom else PosatoNavigationPlacement.Sidebar
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    PosatoNavigationScaffold(
        modifier = modifier.fillMaxSize(),
        placement = placement,
        headerContent = {
            if (placement == PosatoNavigationPlacement.Sidebar || !keyboardVisible) {
                PrototypeNavigationHeader(state, brandFocus, onOpenControls)
            }
        },
        navigationContent = {
            if (placement == PosatoNavigationPlacement.Sidebar || !keyboardVisible) {
                PrototypeDestinationNavigation(state, placement, onAction)
            }
        },
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
