package app.posato.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

/**
 * One destination's screen stack on Navigation 3. The stack is derived from the screen's own state, and
 * [onBack] is the top screen's explicit Back action, so the system back gesture, the Mac back command,
 * and the visible Back button share one path. While [backEnabled] is false only the top screen is kept,
 * which leaves the system back with nothing to return to.
 */
@Composable
internal fun <T : Any> PosatoNavStack(
    backStack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    backEnabled: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    val shown = if (backEnabled) backStack else backStack.takeLast(1)
    val clipped = modifier.clipToBounds()
    val entryProvider = { key: T -> NavEntry(key) { content(key) } }
    if (platformReducesMotion()) {
        NavDisplay(
            backStack = shown,
            modifier = clipped,
            contentAlignment = contentAlignment,
            onBack = onBack,
            transitionSpec = { noMotion() },
            popTransitionSpec = { noMotion() },
            predictivePopTransitionSpec = { noMotion() },
            entryProvider = entryProvider,
        )
    } else {
        NavDisplay(backStack = shown, modifier = clipped, contentAlignment = contentAlignment, onBack = onBack, entryProvider = entryProvider)
    }
}

/** Whether the system asks for reduced motion; screen changes then happen without a slide. */
@Composable
internal expect fun platformReducesMotion(): Boolean

private fun noMotion(): ContentTransform {
    return ContentTransform(EnterTransition.None, ExitTransition.None)
}
