package app.posato.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

/**
 * One destination's screen stack on Navigation 3. The stack is derived from the screen's own state, and
 * [onBack] is the top screen's explicit Back action, so the system back gesture, the Mac back command,
 * and the visible Back button share one path. While [backEnabled] is false the stack's back dispatcher
 * is off, so no back reaches it and the stack stays as it is.
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
    val clipped = modifier.clipToBounds()
    val entryProvider = { key: T -> NavEntry(key) { content(key) } }
    val dispatcher = rememberNavigationEventDispatcherOwner(enabled = backEnabled)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcher) {
        PosatoNavDisplay(backStack, onBack, contentAlignment, entryProvider, clipped)
    }
}

@Composable
private fun <T : Any> PosatoNavDisplay(
    shown: List<T>,
    onBack: () -> Unit,
    contentAlignment: Alignment,
    entryProvider: (T) -> NavEntry<T>,
    modifier: Modifier = Modifier,
) {
    if (platformReducesMotion()) {
        NavDisplay(
            backStack = shown,
            modifier = modifier,
            contentAlignment = contentAlignment,
            onBack = onBack,
            transitionSpec = { noMotion() },
            popTransitionSpec = { noMotion() },
            predictivePopTransitionSpec = { noMotion() },
            entryProvider = entryProvider,
        )
    } else {
        NavDisplay(backStack = shown, modifier = modifier, contentAlignment = contentAlignment, onBack = onBack, entryProvider = entryProvider)
    }
}

/**
 * The last non-null [value] seen by this screen entry. A screen leaving the stack is still drawn while it
 * slides away, after the state that opened it is already cleared; it keeps showing what it showed.
 */
@Composable
internal fun <T : Any> rememberLastPresent(value: T?): T? {
    val last = remember { LastPresent<T>() }
    if (value != null) last.value = value
    return value ?: last.value
}

private class LastPresent<T : Any> {
    var value: T? = null
}

/** Whether the system asks for reduced motion; screen changes then happen without a slide. */
@Composable
internal expect fun platformReducesMotion(): Boolean

private fun noMotion(): ContentTransform {
    return ContentTransform(EnterTransition.None, ExitTransition.None)
}
