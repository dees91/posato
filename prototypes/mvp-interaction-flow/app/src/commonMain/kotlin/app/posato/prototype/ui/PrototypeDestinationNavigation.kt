package app.posato.prototype.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.posato.prototype.designsystem.PosatoBottomNavigation
import app.posato.prototype.designsystem.PosatoBottomNavigationItem
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDeviceLabel
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoNavigationPlacement
import app.posato.prototype.designsystem.PosatoSidebarNavigationItem
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoWordmark
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.SessionAction

@Composable
internal fun PrototypeNavigationHeader(
    state: PrototypeState,
    brandFocus: FocusRequester,
    onOpenControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(PosatoSpace.Section).then(
            if (state.platform == PrototypePlatform.Mac) Modifier.padding(top = PosatoSpace.Section + PosatoSpace.Small) else Modifier,
        ),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
    ) {
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
        if (!state.onboardingComplete) PosatoCaption("A quiet pause, in a few steps.")
    }
}

@Composable
internal fun PrototypeDestinationNavigation(
    state: PrototypeState,
    placement: PosatoNavigationPlacement,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = state.surface in setOf(PrototypeSurface.Items, PrototypeSurface.DomainEditor, PrototypeSurface.AppPicker)
    when (placement) {
        PosatoNavigationPlacement.Bottom -> if (state.onboardingComplete) {
            PosatoBottomNavigation(modifier) {
                PosatoBottomNavigationItem(
                    selected = !items,
                    onClick = { onAction(SessionAction.ReturnToSession) },
                    iconContent = { PosatoIcon(PosatoIcons.Pause, null, Modifier.size(PosatoSize.LargeIcon)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Session") }
                PosatoBottomNavigationItem(
                    selected = items,
                    onClick = { onAction(ItemAction.OpenItems) },
                    iconContent = { PosatoIcon(PosatoIcons.Items, null, Modifier.size(PosatoSize.LargeIcon)) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.session.active,
                ) { Text("Paused items") }
            }
        }

        PosatoNavigationPlacement.Sidebar -> Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = PosatoSpace.Medium, vertical = PosatoSpace.Small),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            if (state.onboardingComplete) {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                    PosatoSidebarNavigationItem(
                        selected = !items,
                        onClick = { onAction(SessionAction.ReturnToSession) },
                        iconContent = { PosatoIcon(PosatoIcons.Pause, null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Session") }
                    PosatoSidebarNavigationItem(
                        selected = items,
                        onClick = { onAction(ItemAction.OpenItems) },
                        iconContent = { PosatoIcon(PosatoIcons.Items, null) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.session.active,
                    ) { Text("Paused items") }
                }
            }
            PosatoDeviceLabel("On this ${state.platform.label}", Modifier.padding(horizontal = PosatoSpace.Medium))
        }
    }
}
