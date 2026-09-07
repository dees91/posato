package app.posato.prototype.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

@Composable
fun PosatoMark(modifier: Modifier = Modifier) {
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
fun PosatoWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        PosatoMark()
        Text("posato", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun PosatoIntervalArtwork(modifier: Modifier = Modifier) {
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

private val MarkInset = 2.dp
private val MarkStroke = 7.dp
private val MarkLength = 25.dp
private const val MARK_ANGLE = 8f
private val ArtInset = 17.dp
private val ArtStroke = 36.dp
private val ArtLength = 103.dp
private const val ART_ANGLE = 10f
