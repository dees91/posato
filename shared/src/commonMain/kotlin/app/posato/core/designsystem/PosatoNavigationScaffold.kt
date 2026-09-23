package app.posato.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

@Composable
internal fun PosatoNavigationScaffold(
    placement: PosatoNavigationPlacement,
    headerContent: @Composable () -> Unit,
    navigationContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    val latestHeader by rememberUpdatedState(headerContent)
    val latestNavigation by rememberUpdatedState(navigationContent)
    val header = remember { movableContentOf { latestHeader() } }
    val navigation = remember { movableContentOf { latestNavigation() } }
    val sidebar = placement == PosatoNavigationPlacement.Sidebar
    Row(modifier.background(MaterialTheme.colorScheme.surface)) {
        if (sidebar) {
            Column(
                Modifier.width(PosatoSize.NavigationSidebar).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                header()
                navigation()
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (!sidebar) {
                header()
            }
            NavigationBody(Modifier.weight(1f).fillMaxWidth(), content)
            if (!sidebar) {
                navigation()
            }
        }
    }
}

@Composable
private fun NavigationBody(
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        content(if (maxWidth < PosatoSize.CompactBreakpoint) PosatoLayout.Compact else PosatoLayout.Expanded)
    }
}
