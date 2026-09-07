package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

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
