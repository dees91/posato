package app.posato.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun PosatoBottomNavigation(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // An iOS tab bar is as low as its items allow, and on a wide screen they gather in the middle instead of spreading
    // across it.
    val rowPadding = if (platformUsesCupertinoChrome) 0.dp else PosatoSpace.Small
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PosatoDivider()
        CappedFontScale(TAB_FONT_SCALE_LIMIT) {
            Row(
                Modifier.then(if (platformUsesCupertinoChrome) Modifier.widthIn(max = TabBarMaxWidth) else Modifier)
                    .fillMaxWidth().padding(horizontal = PosatoSpace.Section, vertical = rowPadding).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
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
    val fill = if (platformUsesCupertinoChrome) PosatoControlDefaults.Transparent else MaterialTheme.colorScheme.primaryContainer
    val itemPadding = if (platformUsesCupertinoChrome) PosatoSpace.Tiny else PosatoSpace.Small
    DestinationSurface(selected, fill, onClick, modifier, enabled) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = PosatoSpace.Small, vertical = itemPadding),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            iconContent()
            ProvideTextStyle(
                MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                content,
            )
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
    // An iPad sidebar marks the chosen row with a quiet fill and the accent, in the text size of a list row.
    val fill = if (platformUsesCupertinoChrome) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer
    val style = if (platformUsesCupertinoChrome) {
        PosatoTypography.BarAction
    } else {
        MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
        )
    }
    DestinationSurface(selected, fill, onClick, modifier, enabled, sidebar = true) {
        Row(
            Modifier.fillMaxWidth().padding(PosatoSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            ProvideTextStyle(style, content)
        }
    }
}

@Composable
private fun DestinationSurface(
    selected: Boolean,
    selectedFill: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sidebar: Boolean = false,
    content: @Composable () -> Unit,
) {
    // An iPad sidebar reads its rows in the text color, as lists do; a tab bar greys the tabs not chosen.
    val idle = if (platformUsesCupertinoChrome && sidebar) {
        MaterialTheme.colorScheme.onSurface
    } else if (platformUsesCupertinoChrome && !posatoIsLight()) {
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant, IDLE_TAB_DARK_SHARE)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val interaction = remember { MutableInteractionSource() }
    val keyboardFocused by interaction.collectIsKeyboardFocusedAsState()
    val color = if (selected) MaterialTheme.colorScheme.primary else idle
    Surface(
        modifier = modifier.heightIn(min = PosatoSize.Control).semantics { role = Role.Tab }
            .keyboardFocusRing({ keyboardFocused }, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.large,
        color = if (selected) selectedFill else PosatoControlDefaults.Transparent,
        contentColor = color.copy(alpha = if (enabled) 1f else PosatoControlDefaults.DISABLED_ALPHA),
        content = content,
    )
}

@Preview(name = "Bottom navigation", widthDp = 390)
@Composable
private fun PosatoBottomNavigationPreview() {
    PosatoComponentPreview {
        PosatoBottomNavigation {
            PosatoBottomNavigationItem(
                modifier = Modifier.weight(1f),
                selected = true,
                onClick = {},
                iconContent = { PosatoIcon(PosatoIcons.Pause, null) },
            ) { Text("Session") }
            PosatoBottomNavigationItem(
                modifier = Modifier.weight(1f),
                selected = false,
                onClick = {},
                iconContent = { PosatoIcon(PosatoIcons.Items, null) },
            ) { Text("Pause sets") }
        }
    }
}

@Preview(name = "Sidebar navigation states", widthDp = 280)
@Composable
private fun PosatoSidebarNavigationPreview() {
    PosatoComponentPreview {
        PosatoSidebarNavigationItem(selected = true, onClick = {}, iconContent = { PosatoIcon(PosatoIcons.Pause, null) }) { Text("Session") }
        PosatoSidebarNavigationItem(selected = false, onClick = {}, iconContent = { PosatoIcon(PosatoIcons.Items, null) }) { Text("Pause sets") }
        PosatoSidebarNavigationItem(
            selected = false,
            onClick = {},
            enabled = false,
            iconContent = { PosatoIcon(PosatoIcons.Items, null) },
        ) { Text("Unavailable") }
    }
}

private const val IDLE_TAB_DARK_SHARE = 0.7f
private const val TAB_FONT_SCALE_LIMIT = 1.3f

private val TabBarMaxWidth = 560.dp
