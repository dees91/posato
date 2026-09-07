package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoNotice(
    modifier: Modifier = Modifier,
    tone: PosatoTone = PosatoTone.Neutral,
    announceChanges: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    val background = when (tone) {
        PosatoTone.Neutral -> palette.surfaceContainer
        PosatoTone.Positive -> palette.secondaryContainer
        PosatoTone.Caution -> palette.tertiaryContainer
        PosatoTone.Critical -> palette.errorContainer
    }
    val foreground = when (tone) {
        PosatoTone.Neutral -> palette.onSurfaceVariant
        PosatoTone.Positive -> palette.onSecondaryContainer
        PosatoTone.Caution -> palette.onTertiaryContainer
        PosatoTone.Critical -> palette.onErrorContainer
    }

    Surface(
        modifier = modifier.semantics { if (announceChanges) liveRegion = LiveRegionMode.Polite },
        color = background,
        contentColor = foreground,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.padding(PosatoSpace.Large), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            leadingContent?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                content()
                actionContent?.invoke()
            }
        }
    }
}

@Preview(name = "Notice tones", widthDp = 390)
@Preview(name = "Notice tones · dark", widthDp = 390, uiMode = 0x20)
@Composable
private fun PosatoNoticePreview() {
    PosatoComponentPreview {
        PosatoNotice { Text("Saved on this device.") }
        PosatoNotice(tone = PosatoTone.Positive, leadingContent = { PosatoIcon(PosatoIcons.Check, null) }) {
            Text("Your selection is saved.")
        }
        PosatoNotice(tone = PosatoTone.Caution) { Text("Blocking is not connected yet.") }
        PosatoNotice(
            tone = PosatoTone.Critical,
            actionContent = { PosatoButton(onClick = {}, style = PosatoButtonStyle.Quiet) { Text("Try again") } },
        ) { Text("Your changes could not be saved.") }
    }
}
