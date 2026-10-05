package app.posato.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Work in progress without a known end: the spoked indicator the Apple hosts show, Material's ring on Android.
 */
@Composable
internal fun PosatoActivityIndicator(modifier: Modifier = Modifier) {
    if (platformUsesMaterialRipple) {
        CircularProgressIndicator(modifier)
    } else {
        SpokedIndicator(modifier)
    }
}

@Composable
private fun SpokedIndicator(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val turn = rememberInfiniteTransition()
        .animateFloat(0f, SPOKES.toFloat(), infiniteRepeatable(tween(TURN_MILLIS, easing = LinearEasing)))
    Canvas(modifier.size(IndicatorSize).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val head = turn.value.toInt() % SPOKES
        val stroke = size.minDimension * SPOKE_WIDTH_SHARE
        val outer = size.minDimension / 2 - stroke / 2
        val inner = size.minDimension / 2 * SPOKE_INNER_SHARE
        repeat(SPOKES) { spoke ->
            val behind = (head - spoke + SPOKES) % SPOKES
            val alpha = 1f - behind.toFloat() / SPOKES * (1f - TAIL_ALPHA)
            rotate(spoke * FULL_TURN / SPOKES) {
                drawLine(
                    color = color.copy(alpha = color.alpha * alpha),
                    start = Offset(center.x, center.y - inner),
                    end = Offset(center.x, center.y - outer),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private val IndicatorSize = 20.dp
private const val SPOKES = 8
private const val TURN_MILLIS = 800
private const val FULL_TURN = 360f
private const val SPOKE_WIDTH_SHARE = 0.11f
private const val SPOKE_INNER_SHARE = 0.42f
private const val TAIL_ALPHA = 0.25f

@Preview(name = "Activity indicator")
@Composable
private fun PosatoActivityIndicatorPreview() {
    PosatoComponentPreview { PosatoActivityIndicator() }
}
