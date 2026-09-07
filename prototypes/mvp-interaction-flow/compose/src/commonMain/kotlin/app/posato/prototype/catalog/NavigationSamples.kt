package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoBottomNavigation
import app.posato.prototype.designsystem.PosatoBottomNavigationItem
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSidebarNavigationItem
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace

@Composable
internal fun NavigationSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableIntStateOf(0) }
    val destinations = listOf("Session" to PosatoIcons.Pause, "Paused items" to PosatoIcons.Items)
    PosatoSection(modifier = modifier, titleContent = { Text("Main destinations") }) {
        PosatoCaption("Bottom navigation on iPhone; sidebar navigation on Mac. Content tabs remain inside the destination.")
        PosatoBottomNavigation(Modifier.fillMaxWidth()) {
            destinations.forEachIndexed { index, (label, icon) ->
                PosatoBottomNavigationItem(
                    selected = selected == index,
                    onClick = {
                        selected = index
                        onAction("$label destination selected.")
                    },
                    iconContent = { PosatoIcon(icon, null, Modifier.size(PosatoSize.LargeIcon)) },
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }
        Column(Modifier.width(PosatoSize.NavigationSidebar).selectableGroup(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            destinations.forEachIndexed { index, (label, icon) ->
                PosatoSidebarNavigationItem(
                    selected = selected == index,
                    onClick = {
                        selected = index
                        onAction("$label destination selected.")
                    },
                    iconContent = { PosatoIcon(icon, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(label) }
            }
        }
    }
}
