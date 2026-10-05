package app.posato.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SwipeState {
    Closed,
    Open,
}

/**
 * A list row that slides left under the finger to reveal its actions, as Mail and Messages rows do. A fling settles
 * it open or closed, a tap on the open row closes it, and screen readers reach the same actions from the row itself.
 */
@Composable
internal fun PosatoSwipeRow(
    actions: List<PosatoSwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable (rowActions: List<CustomAccessibilityAction>) -> Unit,
) {
    val openOffset = -with(LocalDensity.current) { SwipeActionWidth.toPx() } * actions.size
    val state = remember { AnchoredDraggableState(SwipeState.Closed) }
    LaunchedEffect(openOffset) {
        state.updateAnchors(
            DraggableAnchors {
                SwipeState.Closed at 0f
                if (actions.isNotEmpty()) SwipeState.Open at openOffset
            },
        )
    }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.animateTo(SwipeState.Closed) } }
    val rowActions = actions.map { action ->
        CustomAccessibilityAction(action.label) {
            action.onClick()
            true
        }
    }
    val revealed = state.targetValue == SwipeState.Open || state.currentValue == SwipeState.Open
    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min).clipToBounds()) {
        Row(
            Modifier.matchParentSize().then(if (revealed) Modifier else Modifier.clearAndSetSemantics {}),
            horizontalArrangement = Arrangement.End,
        ) {
            actions.forEach { action ->
                SwipeActionButton(action, revealed) {
                    close()
                    action.onClick()
                }
            }
        }
        Box(
            Modifier.offset { IntOffset(state.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0, 0) }
                .anchoredDraggable(state, Orientation.Horizontal, flingBehavior = AnchoredDraggableDefaults.flingBehavior(state))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content(rowActions)
            if (revealed) {
                Box(
                    Modifier.matchParentSize().clearAndSetSemantics {}
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
                )
            }
        }
    }
}

@Composable
private fun SwipeActionButton(
    action: PosatoSwipeAction,
    revealed: Boolean,
    onClick: () -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    Box(
        Modifier.width(SwipeActionWidth).fillMaxHeight()
            .background(if (action.destructive) palette.error else palette.primary)
            .focusProperties { canFocus = revealed }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(action.label, color = if (action.destructive) palette.onError else palette.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

private val SwipeActionWidth = 84.dp
