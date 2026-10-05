package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PosatoButtonStyle = PosatoButtonStyle.Primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (style == PosatoButtonStyle.Quiet) {
        PosatoTextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    } else {
        PosatoFilledButton(onClick = onClick, style = style, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
private fun PosatoFilledButton(
    onClick: () -> Unit,
    style: PosatoButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    val container = when (style) {
        PosatoButtonStyle.Primary -> palette.primary
        PosatoButtonStyle.Destructive -> palette.error
        PosatoButtonStyle.Secondary, PosatoButtonStyle.Quiet, PosatoButtonStyle.Compact -> PosatoControlDefaults.Transparent
    }
    val foreground = when (style) {
        PosatoButtonStyle.Primary -> palette.onPrimary
        PosatoButtonStyle.Destructive -> palette.onError
        PosatoButtonStyle.Quiet -> palette.primary
        PosatoButtonStyle.Secondary, PosatoButtonStyle.Compact -> palette.onSurface
    }
    val border = when (style) {
        PosatoButtonStyle.Secondary, PosatoButtonStyle.Compact -> BorderStroke(PosatoSpace.Hairline, palette.outlineVariant)
        PosatoButtonStyle.Primary, PosatoButtonStyle.Quiet, PosatoButtonStyle.Destructive -> null
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(
        modifier = modifier.heightIn(min = PosatoSize.Control)
            .graphicsLayer { alpha = if (pressed && platformUsesCupertinoChrome) PosatoControlDefaults.PRESSED_ALPHA else 1f },
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        elevation = null,
        border = border,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = foreground,
            disabledContainerColor = container.copy(alpha = container.alpha * PosatoControlDefaults.DISABLED_ALPHA),
            disabledContentColor = foreground.copy(alpha = foreground.alpha * PosatoControlDefaults.DISABLED_ALPHA),
        ),
        contentPadding = if (style == PosatoButtonStyle.Compact) PosatoControlDefaults.CompactPadding else PosatoControlDefaults.ContentPadding,
        content = content,
    )
}

/**
 * A text-only action. Its label sits on the content edge like the text around it: there is no container and no
 * side padding, the label dims while pressed, and keyboard focus underlines it.
 */
@Composable
internal fun PosatoTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.heightIn(min = PosatoSize.Control)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer {
                alpha = when {
                    !enabled -> PosatoControlDefaults.DISABLED_ALPHA
                    pressed -> PosatoControlDefaults.PRESSED_ALPHA
                    else -> 1f
                }
            }
            .drawBehind {
                if (focused) {
                    val stroke = PosatoSpace.Hairline.toPx() * 2
                    val y = size.height - PosatoSpace.Small.toPx()
                    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides color, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            content()
        }
    }
}

@Preview(name = "Button styles", widthDp = 390)
@Preview(name = "Button styles · dark", widthDp = 390, uiMode = 0x20)
@Composable
private fun PosatoButtonPreview() {
    PosatoComponentPreview {
        PosatoButtonStyle.entries.forEach { style ->
            PosatoActionRow {
                PosatoButton(onClick = {}, style = style) { Text(style.name) }
                PosatoButton(onClick = {}, style = style, enabled = false) { Text("Disabled") }
            }
        }
    }
}
