package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

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

@Preview(name = "Compact hero", widthDp = 390)
@Composable
private fun PosatoHeroCompactPreview() {
    PosatoComponentPreview {
        PosatoHero(
            layout = PosatoLayout.Compact,
            headingContent = { PosatoHeading("Room for what matters.", eyebrow = "NO SESSION ACTIVE", layout = PosatoLayout.Compact) },
            supportingContent = { PosatoBody("A quiet pause is ready when you are.") },
            artworkContent = { PosatoIntervalArtwork() },
        )
    }
}

@Preview(name = "Expanded hero", widthDp = 760)
@Composable
private fun PosatoHeroExpandedPreview() {
    PosatoComponentPreview {
        PosatoHero(
            headingContent = { PosatoHeading("Room for what matters.", eyebrow = "NO SESSION ACTIVE") },
            supportingContent = { PosatoBody("A quiet pause is ready when you are.") },
            artworkContent = { PosatoIntervalArtwork() },
        )
    }
}

@Preview(name = "Empty state", widthDp = 390)
@Composable
private fun PosatoEmptyStatePreview() {
    PosatoComponentPreview {
        PosatoEmptyState(
            title = "Nothing here yet",
            description = "Choose the websites you want to pause.",
            actionContent = { PosatoButton(onClick = {}) { Text("Add websites") } },
        )
    }
}
