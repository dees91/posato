package app.posato.prototype.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun PosatoEyebrow(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(modifier = modifier, text = text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
}

@Composable
fun PosatoTitle(
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
fun PosatoBody(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(modifier = modifier, text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun PosatoCaption(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(modifier = modifier, text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun PosatoHeading(
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
