package app.posato.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

@Composable
internal fun PosatoAppScaffold(
    navigationContent: @Composable (PosatoLayout) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    val latestNavigation by rememberUpdatedState(navigationContent)
    val latestContent by rememberUpdatedState(content)
    val navigation = remember { movableContentOf<PosatoLayout> { latestNavigation(it) } }
    val body = remember { movableContentOf<PosatoLayout> { latestContent(it) } }
    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        if (maxWidth < PosatoSize.CompactBreakpoint) {
            CompactAppLayout(navigationContent = navigation, content = body)
        } else {
            ExpandedAppLayout(navigationContent = navigation, content = body)
        }
    }
}

@Composable
private fun CompactAppLayout(
    navigationContent: @Composable (PosatoLayout) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    Column(modifier = modifier) {
        Box(Modifier.fillMaxWidth().padding(PosatoSpace.Large)) { navigationContent(PosatoLayout.Compact) }
        PosatoDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) { content(PosatoLayout.Compact) }
    }
}

@Composable
private fun ExpandedAppLayout(
    navigationContent: @Composable (PosatoLayout) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    Row(modifier = modifier) {
        Box(Modifier.width(PosatoSize.Sidebar).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
            navigationContent(PosatoLayout.Expanded)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content(PosatoLayout.Expanded) }
    }
}
