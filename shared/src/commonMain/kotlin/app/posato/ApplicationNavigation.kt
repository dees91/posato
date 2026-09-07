package app.posato

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
import androidx.compose.ui.unit.dp
import app.posato.core.designsystem.PosatoBottomNavigation
import app.posato.core.designsystem.PosatoBottomNavigationItem
import app.posato.core.designsystem.PosatoDeviceLabel
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoNavigationPlacement
import app.posato.core.designsystem.PosatoSidebarNavigationItem
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoWordmark

@Composable
internal fun ApplicationNavigationHeader(placement: PosatoNavigationPlacement) {
    val topInset = if (placement == PosatoNavigationPlacement.Sidebar) PosatoSpace.Spacious else 0.dp
    Column(Modifier.padding(PosatoSpace.Section).padding(top = topInset)) {
        PosatoWordmark(Modifier.heightIn(min = PosatoSize.Control))
    }
}

@Composable
internal fun ApplicationNavigation(
    placement: PosatoNavigationPlacement,
    showingSession: Boolean,
    onSelect: (Boolean) -> Unit
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
                    selected = showingSession,
                    onClick = { onSelect(true) },
                    iconContent = { PosatoIcon(PosatoIcons.Pause, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Session") }
                PosatoSidebarNavigationItem(
                    selected = !showingSession,
                    onClick = { onSelect(false) },
                    iconContent = { PosatoIcon(PosatoIcons.Items, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Paused items") }
            }
            PosatoDeviceLabel("On this Mac", Modifier.padding(horizontal = PosatoSpace.Medium))
        }
    }
}
