package app.posato.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview

/** The days of a week, spread across the row as round toggles. */
@Composable
internal fun PosatoDayRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    CappedFontScale(DAY_FONT_SCALE_LIMIT) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, content = content)
    }
}

/**
 * One day of a repeating choice, the way iOS picks days: the day's initial in a circle that fills with the accent
 * when chosen. Assistive technology reads the full [name] and whether it is chosen.
 */
@Composable
internal fun PosatoDayToggle(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val keyboardFocused by interaction.collectIsKeyboardFocusedAsState()
    Box(
        modifier = modifier.size(PosatoSize.Control)
            .clip(CircleShape)
            .semantics { contentDescription = name }
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .background(if (selected) palette.primary else palette.surfaceContainerHighest)
            .keyboardFocusRing({ keyboardFocused }, palette.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            modifier = Modifier.clearAndSetSemantics {},
            style = PosatoTypography.BarAction,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.onPrimary else palette.onSurface,
        )
    }
}

private const val DAY_FONT_SCALE_LIMIT = 1.3f

@Preview(name = "Day toggles", widthDp = 390)
@Composable
private fun PosatoDayTogglePreview() {
    PosatoComponentPreview {
        PosatoDayRow {
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").forEachIndexed { index, day ->
                PosatoDayToggle(day, selected = index < 5, onClick = {})
            }
        }
    }
}
