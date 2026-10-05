package app.posato.core.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * A screen stack that moves like UIKit's navigation controller: a pushed screen slides in over the one below,
 * which drifts a third of the width behind it and dims; the edge swipe tracks the finger, and on release the
 * screen springs on with the finger's velocity. Every motion starts from where the screen is, so a push, a pop,
 * and a swipe can interrupt one another.
 */
@Composable
internal fun <T : Any> CupertinoNavDisplay(
    backStack: List<T>,
    onBack: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val stack = remember { CupertinoStack(backStack, holder) }
    val motion = if (reduceMotion) ReducedMotion else ScreenSpring
    val top = backStack.last()
    LaunchedEffect(top) { stack.settle(backStack, motion) }
    CupertinoBackGesture(stack, backStack, onBack, motion)
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.clipToBounds(), contentAlignment = contentAlignment) {
        val layers = stack.layers
        // The screen below the top stays composed while hidden, so an edge swipe starts on a screen that is ready.
        val parked = stack.parked
        listOfNotNull(layers.under ?: parked, layers.over).forEach { entry ->
            key(stackKey(entry)) {
                val layer = if (entry == parked) Modifier.parked() else stack.layerModifier(isUnder = entry == layers.under)
                Box(
                    layer.fillMaxSize().background(surface),
                    contentAlignment = contentAlignment,
                ) {
                    holder.SaveableStateProvider(stackKey(entry)) { content(entry) }
                }
            }
        }
    }
}

/** The system edge swipe, followed frame by frame and finished with the finger's velocity. */
@Composable
private fun <T : Any> CupertinoBackGesture(
    stack: CupertinoStack<T>,
    backStack: List<T>,
    onBack: () -> Unit,
    motion: AnimationSpec<Float>,
) {
    val scope = rememberCoroutineScope()
    val latestStack by rememberUpdatedState(backStack)
    val latestOnBack by rememberUpdatedState(onBack)
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    LaunchedEffect(backState) {
        snapshotFlow { (backState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress }
            .collect { progress -> if (progress != null) stack.track(progress, latestStack) }
    }
    NavigationBackHandler(
        state = backState,
        isBackEnabled = backStack.size > 1,
        onBackCancelled = {
            val velocity = stack.gesture.release()
            scope.launch { stack.restore(latestStack.last(), velocity, motion) }
        },
        onBackCompleted = {
            val before = latestStack.last()
            stack.gesture.releaseVelocity = stack.gesture.release()
            latestOnBack()
            scope.launch {
                withFrameNanos {}
                if (stack.settledTop == before && latestStack.last() == before) {
                    stack.restore(before, stack.gesture.takeReleaseVelocity(), motion)
                }
            }
        },
    )
}

/** Which screens are drawn, and how far the upper one has slid in: 1 is in place, 0 is off the trailing edge. */
private class CupertinoStack<T : Any>(
    initial: List<T>,
    private val holder: SaveableStateHolder,
) {
    val position = Animatable(1f)
    val gesture = SwipeTracker()
    var layers by mutableStateOf(StackLayers(under = null, over = initial.last()))
        private set
    private var settled by mutableStateOf(initial)

    /** The settled screen below the top, kept composed but unplaced while no motion shows it. */
    val parked: T?
        get() {
            return settled.getOrNull(settled.size - 2).takeIf { layers.under == null && it != layers.over }
        }

    val settledTop: T
        get() {
            return settled.last()
        }

    suspend fun settle(
        backStack: List<T>,
        motion: AnimationSpec<Float>,
    ) {
        val previousStack = settled
        val previous = previousStack.last()
        val top = backStack.last()
        settled = backStack
        if (previous == top) return
        if (backStack.contains(previous)) {
            push(previous, top, motion)
        } else if (previousStack.contains(top)) {
            pop(previous, top, motion)
        } else {
            gesture.takeReleaseVelocity()
            show(top)
            holder.removeState(stackKey(previous))
        }
    }

    suspend fun track(
        progress: Float,
        stack: List<T>,
    ) {
        if (stack.size < 2) return
        if (!gesture.tracking) {
            gesture.start()
            layers = StackLayers(under = stack[stack.size - 2], over = stack.last())
        }
        gesture.add(progress)
        position.snapTo(1f - progress)
    }

    suspend fun restore(
        top: T,
        velocity: Float,
        motion: AnimationSpec<Float>,
    ) {
        position.animateTo(1f, motion, initialVelocity = velocity)
        layers = StackLayers(under = null, over = top)
    }

    fun layerModifier(isUnder: Boolean): Modifier {
        return if (isUnder) {
            Modifier.graphicsLayer { translationX = -size.width * UNDER_DRIFT * position.value }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black, alpha = UNDER_DIM * position.value)
                }
        } else {
            Modifier.graphicsLayer { translationX = size.width * (1f - position.value) }
                .drawBehind { if (layers.under != null) drawEdgeShadow() }
        }
    }

    private suspend fun push(
        previous: T,
        top: T,
        motion: AnimationSpec<Float>,
    ) {
        val resuming = layers.under == previous && layers.over == top
        layers = StackLayers(under = previous, over = top)
        if (!resuming) position.snapTo(0f)
        position.animateTo(1f, motion)
        layers = StackLayers(under = null, over = top)
    }

    private suspend fun pop(
        previous: T,
        top: T,
        motion: AnimationSpec<Float>,
    ) {
        layers = StackLayers(under = top, over = previous)
        position.animateTo(0f, motion, initialVelocity = gesture.takeReleaseVelocity())
        show(top)
        holder.removeState(stackKey(previous))
    }

    private suspend fun show(top: T) {
        layers = StackLayers(under = null, over = top)
        position.snapTo(1f)
    }
}

/**
 * Measures a parked screen without placing it: it keeps its composition and state, but is not drawn, cannot be
 * touched or focused, and stays out of the accessibility tree.
 */
private fun Modifier.parked(): Modifier {
    return clearAndSetSemantics {}.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }
}

private data class StackLayers<T : Any>(
    val under: T?,
    val over: T,
)

/** The edge swipe's progress over time, so its release velocity can carry into the spring that finishes it. */
private class SwipeTracker {
    private val tracker = VelocityTracker1D(isDataDifferential = false)
    private var started = TimeSource.Monotonic.markNow()
    var tracking = false
        private set
    var releaseVelocity = 0f

    fun start() {
        tracker.resetTracking()
        started = TimeSource.Monotonic.markNow()
        tracking = true
    }

    fun add(progress: Float) {
        tracker.addDataPoint(started.elapsedNow().inWholeMilliseconds, progress)
    }

    fun release(): Float {
        if (!tracking) return 0f
        tracking = false
        return -tracker.calculateVelocity()
    }

    fun takeReleaseVelocity(): Float {
        val velocity = releaseVelocity
        releaseVelocity = 0f
        return velocity
    }
}

private fun stackKey(entry: Any): String {
    return entry.toString()
}

private fun DrawScope.drawEdgeShadow() {
    val width = EdgeShadowWidth.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = EDGE_SHADOW_ALPHA)),
            startX = -width,
            endX = 0f,
        ),
        topLeft = Offset(-width, 0f),
        size = Size(width, size.height),
    )
}

private const val UNDER_DRIFT = 0.3f
private const val UNDER_DIM = 0.1f
private const val EDGE_SHADOW_ALPHA = 0.12f
private val EdgeShadowWidth = 10.dp
private const val SCREEN_STIFFNESS = 230f
private const val INSTANT_STIFFNESS = 100_000f
private const val POSITION_THRESHOLD = 0.0005f

private val ScreenSpring: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = SCREEN_STIFFNESS, visibilityThreshold = POSITION_THRESHOLD)
private val ReducedMotion: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = INSTANT_STIFFNESS, visibilityThreshold = POSITION_THRESHOLD)
