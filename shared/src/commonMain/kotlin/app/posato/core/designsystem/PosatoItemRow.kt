package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun PosatoItemList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
        PosatoDivider()
        content()
    }
}

@Composable
internal fun PosatoItemRow(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            Modifier.padding(vertical = PosatoSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                headlineContent()
                supportingContent?.invoke()
            }
            trailingContent?.invoke()
        }
        PosatoDivider()
    }
}

@Composable
internal fun PosatoItemSymbol(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.size(PosatoSize.ItemSymbol),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun PosatoBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PosatoControlDefaults.Transparent,
        border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = PosatoSpace.Small, vertical = PosatoSpace.Tiny),
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
