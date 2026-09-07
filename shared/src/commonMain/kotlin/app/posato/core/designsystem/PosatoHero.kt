package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun PosatoHero(
    headingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Expanded,
    artworkContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            headingContent()
            supportingContent?.invoke()
        }
        if (layout == PosatoLayout.Expanded) artworkContent?.invoke()
    }
}

@Composable
internal fun PosatoEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        description?.let { PosatoBody(it) }
        actionContent?.invoke()
    }
}
