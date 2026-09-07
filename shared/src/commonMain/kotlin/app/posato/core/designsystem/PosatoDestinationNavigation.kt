package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun PosatoBottomNavigation(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier) {
        PosatoDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PosatoSpace.Section, vertical = PosatoSpace.Small).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun PosatoBottomNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    DestinationSurface(selected, onClick, modifier, enabled) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = PosatoSpace.Small, vertical = PosatoSpace.Small),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            iconContent()
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), content)
        }
    }
}

@Composable
internal fun PosatoSidebarNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    DestinationSurface(selected, onClick, modifier, enabled) {
        Row(
            Modifier.fillMaxWidth().padding(PosatoSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), content)
        }
    }
}

@Composable
private fun DestinationSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.heightIn(min = PosatoSize.Control).semantics { role = Role.Tab },
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else PosatoControlDefaults.Transparent,
        contentColor = color.copy(alpha = if (enabled) 1f else PosatoControlDefaults.DISABLED_ALPHA),
        content = content,
    )
}
