package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoEyebrow(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(modifier = modifier, text = text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
}

@Composable
internal fun PosatoTitle(
    text: String,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Expanded
) {
    Text(
        modifier = modifier.semantics { heading() },
        text = text,
        style = if (layout == PosatoLayout.Compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
    )
}

@Composable
internal fun PosatoBody(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(modifier = modifier, text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun PosatoCaption(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(modifier = modifier, text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun PosatoHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    description: String? = null,
    layout: PosatoLayout = PosatoLayout.Expanded,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        eyebrow?.let { PosatoEyebrow(it) }
        PosatoTitle(title, layout = layout)
        description?.let { PosatoBody(it) }
    }
}

@Preview(name = "Text hierarchy", widthDp = 390)
@Composable
private fun PosatoTextPreview() {
    PosatoComponentPreview {
        PosatoEyebrow("YOUR NEXT PAUSE")
        PosatoTitle("Room for what matters.", layout = PosatoLayout.Compact)
        PosatoTitle("Room for what matters.", layout = PosatoLayout.Expanded)
        PosatoBody("A quiet pause is ready when you are.")
        PosatoCaption("Saved on this device.")
        PosatoHeading(
            title = "How much space do you need?",
            eyebrow = "YOUR NEXT PAUSE",
            description = "Give this pause a clear ending.",
            layout = PosatoLayout.Compact,
        )
    }
}
