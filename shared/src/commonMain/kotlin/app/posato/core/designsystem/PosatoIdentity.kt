package app.posato.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun PosatoMark(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(PosatoSize.MarkWidth, PosatoSize.MarkHeight)) {
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = MarkInset)
                .size(MarkStroke, MarkLength).rotate(MARK_ANGLE)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = -MarkInset)
                .size(MarkStroke, MarkLength).rotate(MARK_ANGLE)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

@Composable
internal fun PosatoWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        PosatoMark()
        Text("posato", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun PosatoIntervalArtwork(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(PosatoSize.ArtWidth, PosatoSize.ArtHeight)) {
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = ArtInset)
                .size(ArtStroke, ArtLength).rotate(ART_ANGLE)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        )
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = -ArtInset)
                .size(ArtStroke, ArtLength).rotate(ART_ANGLE)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

/**
 * The interval mark turned to face back: its two forms mirrored so they lean the way a back step goes. It stands
 * in for the navigation bar's back chevron, drawn from the mark's own proportions in the current content color.
 */
@Composable
internal fun PosatoBackMark(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(BackMarkWidth, BackMarkHeight)) {
        val scale = size.height / MARK_UNITS_HIGH
        val stroke = MARK_UNITS_STROKE * scale
        val length = MARK_UNITS_LENGTH * scale
        listOf(
            Offset(size.width - MARK_UNITS_NEAR * scale, size.height - length / 2),
            Offset(MARK_UNITS_NEAR * scale, length / 2),
        ).forEach { center ->
            rotate(-MARK_ANGLE, pivot = center) {
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - (length - stroke) / 2),
                    end = Offset(center.x, center.y + (length - stroke) / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private val BackMarkWidth = 13.dp
private val BackMarkHeight = 17.dp
private const val MARK_UNITS_HIGH = 32f
private const val MARK_UNITS_STROKE = 7f
private const val MARK_UNITS_LENGTH = 25f
private const val MARK_UNITS_NEAR = 5.5f
private val MarkInset = 2.dp
private val MarkStroke = 7.dp
private val MarkLength = 25.dp
private const val MARK_ANGLE = 8f
private val ArtInset = 17.dp
private val ArtStroke = 36.dp
private val ArtLength = 103.dp
private const val ART_ANGLE = 10f

@Preview(name = "Identity", widthDp = 280)
@Composable
private fun PosatoIdentityPreview() {
    PosatoComponentPreview {
        PosatoMark()
        PosatoWordmark()
        PosatoIntervalArtwork()
    }
}
