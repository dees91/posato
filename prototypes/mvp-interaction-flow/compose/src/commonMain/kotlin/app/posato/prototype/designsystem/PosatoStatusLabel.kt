package app.posato.prototype.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PosatoStatusLabel(
    label: String,
    modifier: Modifier = Modifier,
    tone: PosatoTone = PosatoTone.Positive
) {
    val palette = MaterialTheme.colorScheme
    val color = when (tone) {
        PosatoTone.Neutral -> palette.onSurfaceVariant
        PosatoTone.Positive -> palette.primary
        PosatoTone.Caution -> palette.onTertiaryContainer
        PosatoTone.Critical -> palette.error
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(StatusDotSize).background(color, CircleShape))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

private val StatusDotSize = 6.dp
