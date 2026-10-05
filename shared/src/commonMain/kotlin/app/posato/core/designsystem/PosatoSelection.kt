package app.posato.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun PosatoToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    FilterChip(
        modifier = modifier.heightIn(min = PosatoSize.Control),
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = label,
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
internal fun PosatoChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = PosatoSize.Control).semantics { role = Role.RadioButton },
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        color = if (selected) palette.secondaryContainer else palette.surface,
        contentColor = if (selected) palette.onSecondaryContainer else palette.onSurface,
        border = BorderStroke(PosatoSpace.Hairline, if (selected) palette.primary else palette.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(PosatoSpace.Large),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
            supportingContent?.invoke()
        }
    }
}

/** An on/off setting: its label and explanation, with a switch on the trailing edge. The whole row toggles. */
@Composable
internal fun PosatoSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.heightIn(min = PosatoSize.Control)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = null,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = PosatoSpace.Small),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { content() }
            supportingContent?.invoke()
        }
        PosatoSwitch(checked = checked, enabled = enabled)
    }
}

/** The switch drawn in the app's palette: a pill track and a round thumb that springs across. */
@Composable
internal fun PosatoSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = MaterialTheme.colorScheme
    val travel by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = SWITCH_DAMPING, stiffness = Spring.StiffnessMediumLow),
        label = "switchTravel",
    )
    val track = lerp(palette.outlineVariant, palette.primary, travel)
    val thumb = if (palette.surface.luminance() > HALF) Color.White else palette.onSurface
    Canvas(
        modifier.size(SwitchWidth, SwitchHeight).graphicsLayer { alpha = if (enabled) 1f else PosatoControlDefaults.DISABLED_ALPHA },
    ) {
        val radius = size.height / 2
        drawRoundRect(track, cornerRadius = CornerRadius(radius))
        val inset = SwitchInset.toPx()
        val thumbRadius = radius - inset
        val x = radius + (size.width - 2 * radius) * travel
        drawCircle(Color.Black.copy(alpha = THUMB_SHADOW), thumbRadius, Offset(x, radius + inset / 2))
        drawCircle(thumb, thumbRadius, Offset(x, radius))
    }
}

private val SwitchWidth = 51.dp
private val SwitchHeight = 31.dp
private val SwitchInset = 2.dp
private const val SWITCH_DAMPING = 0.75f
private const val THUMB_SHADOW = 0.12f
private const val HALF = 0.5f

@Composable
internal fun PosatoDurationChoice(
    valueLabel: String,
    unitLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoChoiceTile(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        supportingContent = { PosatoCaption(unitLabel) },
    ) {
        Text(valueLabel, style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(name = "Selection states", widthDp = 390)
@Composable
private fun PosatoSelectionPreview() {
    PosatoComponentPreview {
        PosatoChoiceGroup {
            PosatoToggleButton(selected = true, onClick = {}) { Text("Selected") }
            PosatoToggleButton(selected = false, onClick = {}) { Text("Available") }
            PosatoToggleButton(selected = false, onClick = {}, enabled = false) { Text("Disabled") }
        }
        PosatoChoiceGroup {
            PosatoChoiceTile(selected = true, onClick = {}) { Text("Selected") }
            PosatoChoiceTile(selected = false, onClick = {}, enabled = false) { Text("Disabled") }
        }
        PosatoSwitchRow(checked = true, onCheckedChange = {}, supportingContent = { PosatoCaption("A notice when a pause ends.") }) {
            Text("Pause notifications")
        }
        PosatoSwitchRow(checked = false, onCheckedChange = {}) { Text("Schedule on") }
        PosatoSwitchRow(checked = true, onCheckedChange = {}, enabled = false) { Text("Unavailable setting") }
    }
}

@Preview(name = "Duration choices", widthDp = 390)
@Composable
private fun PosatoDurationChoicePreview() {
    PosatoComponentPreview {
        PosatoChoiceGroup {
            PosatoDurationChoice("25", "minutes", selected = false, onClick = {})
            PosatoDurationChoice("45", "minutes", selected = true, onClick = {})
            PosatoDurationChoice("60", "minutes", selected = false, onClick = {})
        }
    }
}
