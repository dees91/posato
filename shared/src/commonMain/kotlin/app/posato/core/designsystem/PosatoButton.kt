package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PosatoButtonStyle = PosatoButtonStyle.Primary,
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

    Button(
        modifier = modifier.heightIn(min = PosatoSize.Control),
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        elevation = null,
        border = border,
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
