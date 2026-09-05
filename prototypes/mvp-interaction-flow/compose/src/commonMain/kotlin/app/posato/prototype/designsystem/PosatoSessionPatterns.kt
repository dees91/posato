package app.posato.prototype.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PosatoEndTime(
    endTimeLabel: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(PosatoSpace.Large),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosatoIcon(PosatoIcons.Clock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                Text(endTimeLabel, style = MaterialTheme.typography.titleMedium)
                supportingText?.let { PosatoCaption(it) }
            }
        }
    }
}

@Composable
fun PosatoSyncFooter(
    message: String,
    modifier: Modifier = Modifier,
    actionContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
        PosatoDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
            PosatoIcon(PosatoIcons.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            PosatoCaption(message, Modifier.weight(1f))
        }
        actionContent?.invoke()
    }
}

@Composable
fun PosatoPrivacyPoint(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    PosatoItemRow(
        modifier = modifier,
        headlineContent = headlineContent,
        leadingContent = leadingContent,
        supportingContent = supportingContent,
    )
}
