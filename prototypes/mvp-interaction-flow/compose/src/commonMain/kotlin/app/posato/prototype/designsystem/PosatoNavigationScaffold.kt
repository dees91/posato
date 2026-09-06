package app.posato.prototype.designsystem

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
fun PosatoNavigationScaffold(
    placement: PosatoNavigationPlacement,
    headerContent: @Composable () -> Unit,
    navigationContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    val latestHeader by rememberUpdatedState(headerContent)
    val latestNavigation by rememberUpdatedState(navigationContent)
    val latestContent by rememberUpdatedState(content)
    val header = remember { movableContentOf { latestHeader() } }
    val navigation = remember { movableContentOf { latestNavigation() } }
    val body = remember { movableContentOf<PosatoLayout> { latestContent(it) } }
    when (placement) {
        PosatoNavigationPlacement.Bottom -> Column(modifier.background(MaterialTheme.colorScheme.surface)) {
            header()
            NavigationBody(Modifier.weight(1f).fillMaxWidth(), body)
            navigation()
        }

        PosatoNavigationPlacement.Sidebar -> Row(modifier.background(MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier.width(PosatoSize.NavigationSidebar).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                header()
                navigation()
            }
            NavigationBody(Modifier.weight(1f).fillMaxHeight(), body)
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
