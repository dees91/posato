package app.posato

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.posato.core.designsystem.PosatoBottomNavigation
import app.posato.core.designsystem.PosatoBottomNavigationItem
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoControlDefaults
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoDeviceLabel
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNavigationPlacement
import app.posato.core.designsystem.PosatoNavigationScaffold
import app.posato.core.designsystem.PosatoSidebarNavigationItem
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoWordmark
import app.posato.core.designsystem.windowNavigationPlacement

@Composable
internal fun ApplicationNavigationHeader(
    device: PosatoDevice,
    onOpenAbout: (() -> Unit)? = null,
) {
    val topInset = if (device == PosatoDevice.Mac) PosatoSpace.Spacious else 0.dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(PosatoSpace.Section).padding(top = topInset),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosatoWordmark(Modifier.heightIn(min = PosatoSize.Control))
        if (onOpenAbout != null) {
            PosatoButton(onClick = onOpenAbout, style = PosatoButtonStyle.Quiet) { Text("About Posato") }
        }
    }
}

@Composable
internal fun ApplicationNavigation(
    placement: PosatoNavigationPlacement,
    device: PosatoDevice,
    showingSession: Boolean,
    showingInformation: Boolean,
    onSelect: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
) {
    when (placement) {
        PosatoNavigationPlacement.Bottom -> PosatoBottomNavigation {
            PosatoBottomNavigationItem(
                selected = showingSession,
                onClick = { onSelect(true) },
                iconContent = { PosatoIcon(PosatoIcons.Pause, null, Modifier.size(PosatoSize.LargeIcon)) },
                modifier = Modifier.weight(1f),
            ) { Text("Session") }
            PosatoBottomNavigationItem(
                selected = !showingSession,
                onClick = { onSelect(false) },
                iconContent = { PosatoIcon(PosatoIcons.Items, null, Modifier.size(PosatoSize.LargeIcon)) },
                modifier = Modifier.weight(1f),
            ) { Text("Paused items") }
        }

        PosatoNavigationPlacement.Sidebar -> Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PosatoSpace.Medium, vertical = PosatoSpace.Small),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                PosatoSidebarNavigationItem(
                    selected = showingSession && !showingInformation,
                    onClick = { onSelect(true) },
                    iconContent = { PosatoIcon(PosatoIcons.Pause, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Session") }
                PosatoSidebarNavigationItem(
                    selected = !showingSession && !showingInformation,
                    onClick = { onSelect(false) },
                    iconContent = { PosatoIcon(PosatoIcons.Items, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Paused items") }
            }
            val labelInset = PosatoSpace.Medium + PosatoSize.Icon + PosatoSpace.Medium
            val buttonInset = labelInset - PosatoControlDefaults.ContentPadding.calculateStartPadding(LocalLayoutDirection.current)
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                PosatoDeviceLabel("On this ${device.noun}", Modifier.padding(start = labelInset))
                PosatoButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.padding(start = buttonInset),
                    style = PosatoButtonStyle.Quiet,
                ) { Text("About Posato") }
            }
        }
    }
}

@Composable
internal fun ApplicationNavigationScaffold(
    device: PosatoDevice,
    showingSession: Boolean,
    showingInformation: Boolean,
    onSelect: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    val placement = windowNavigationPlacement(device)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val hideNavigation = placement == PosatoNavigationPlacement.Bottom && keyboardVisible
    PosatoNavigationScaffold(
        placement = placement,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing),
        headerContent = {
            if (!hideNavigation) {
                ApplicationNavigationHeader(
                    device,
                    onOpenAbout = onOpenAbout.takeUnless { showingInformation || placement == PosatoNavigationPlacement.Sidebar },
                )
            }
        },
        navigationContent = {
            if (!hideNavigation && (placement == PosatoNavigationPlacement.Sidebar || !showingInformation)) {
                ApplicationNavigation(placement, device, showingSession, showingInformation, onSelect, onOpenAbout)
            }
        },
        content = content,
    )
}
