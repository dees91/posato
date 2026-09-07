package app.posato.prototype.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PosatoNumberWheel(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(value in range)
    val wheel = remember(range) { NumberWheelState(value, range) }
    val change by rememberUpdatedState(onValueChange)
    LaunchedEffect(value, wheel) { wheel.align(value) }
    LaunchedEffect(wheel) {
        snapshotFlow { wheel.scrolledValue() }.collect { next ->
            if (next != null && next != wheel.emitted) {
                wheel.emitted = next
                change(next)
            }
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PosatoCaption(label)
        WheelArrow(label = "Increase $label", increasing = true, enabled = value < range.last, onClick = { change(value + 1) })
        NumberWheelValues(value, range, label, wheel.scroll, change)
        WheelArrow(label = "Decrease $label", increasing = false, enabled = value > range.first, onClick = { change(value - 1) })
    }
}

private class NumberWheelState(
    value: Int,
    val range: IntRange
) {
    val scroll = LazyListState(value - range.first)
    var emitted = value
    private var aligning by mutableStateOf(false)

    suspend fun align(value: Int) {
        if (value == emitted) return
        aligning = true
        try {
            emitted = value
            scroll.scrollToItem(value - range.first)
        } finally {
            aligning = false
        }
    }

    fun scrolledValue(): Int? {
        if (aligning || !scroll.isScrollInProgress) return null
        val layout = scroll.layoutInfo
        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.let { range.first + it.index }
    }
}

@Composable
private fun NumberWheelValues(
    value: Int,
    range: IntRange,
    label: String,
    scroll: LazyListState,
    onValueChange: (Int) -> Unit
) {
    val rowHeight = with(LocalDensity.current) { MaterialTheme.typography.headlineSmall.lineHeight.toDp() + PosatoSpace.Medium }
        .coerceAtLeast(PosatoSize.Control)
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(rowHeight).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(rowHeight * WHEEL_VISIBLE_ROWS).clip(MaterialTheme.shapes.medium)
                .onKeyEvent { event ->
                    val next = when (event.key) {
                        Key.DirectionUp -> value + 1
                        Key.DirectionDown -> value - 1
                        Key.MoveHome -> range.first
                        Key.MoveEnd -> range.last
                        else -> return@onKeyEvent false
                    }.coerceIn(range)
                    if (event.type == KeyEventType.KeyDown) onValueChange(next)
                    true
                }.clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = value.toString()
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                    setProgress { requested ->
                        onValueChange(requested.roundToInt().coerceIn(range))
                        true
                    }
                }.focusable(),
            state = scroll,
            contentPadding = PaddingValues(vertical = rowHeight),
            flingBehavior = rememberSnapFlingBehavior(scroll),
        ) {
            items(count = range.last - range.first + 1, key = { range.first + it }) { index ->
                val number = range.first + index
                Box(
                    modifier = Modifier.fillMaxWidth().height(rowHeight).clickable { onValueChange(number) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        number.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (value == number) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val WHEEL_VISIBLE_ROWS = 3

@Composable
private fun WheelArrow(
    label: String,
    increasing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(modifier = Modifier.sizeIn(minWidth = PosatoSize.Control, minHeight = PosatoSize.Control), onClick = onClick, enabled = enabled) {
        PosatoIcon(if (increasing) PosatoIcons.ChevronUp else PosatoIcons.ChevronDown, contentDescription = label)
    }
}
