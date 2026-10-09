package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoTabBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(PosatoSpace.Tiny).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun PosatoTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Tab,
    contentPadding: PaddingValues = PaddingValues(PosatoSpace.Medium),
    countContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val contentAlpha = if (enabled) 1f else PosatoControlDefaults.DISABLED_ALPHA
    val interaction = remember { MutableInteractionSource() }
    val keyboardFocused by interaction.collectIsKeyboardFocusedAsState()
    Surface(
        modifier = modifier.semantics { this.role = role }
            .keyboardFocusRing({ keyboardFocused }, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
        selected = selected,
        onClick = onClick,
        interactionSource = interaction,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) selectedSegmentColor() else PosatoControlDefaults.Transparent,
        border = if (selected) BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant) else null,
        contentColor = contentColor.copy(alpha = contentAlpha),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PosatoSize.Control).padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) { content() }
            countContent?.let { count ->
                ProvideTextStyle(
                    MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)),
                    count,
                )
            }
        }
    }
}

/** The selected segment stands lighter than its track in both appearances, as a raised piece does. */
@Composable
private fun selectedSegmentColor(): Color {
    val palette = MaterialTheme.colorScheme
    return if (posatoIsLight()) palette.surfaceContainerLowest else palette.surfaceContainerHighest
}

@Preview(name = "Counted tabs", widthDp = 390)
@Preview(name = "Counted tabs · dark", widthDp = 390, uiMode = 0x20)
@Composable
private fun PosatoTabBarPreview() {
    PosatoComponentPreview {
        PosatoTabBar {
            PosatoTab(modifier = Modifier.weight(1f), selected = true, onClick = {}, countContent = { Text("50") }) { Text("Websites") }
            PosatoTab(modifier = Modifier.weight(1f), selected = false, onClick = {}, countContent = { Text("4") }) { Text("Apps") }
        }
        PosatoTabBar {
            PosatoTab(modifier = Modifier.weight(1f), selected = false, onClick = {}) { Text("Websites") }
            PosatoTab(modifier = Modifier.weight(1f), selected = false, onClick = {}, enabled = false) { Text("Unavailable") }
        }
    }
}
