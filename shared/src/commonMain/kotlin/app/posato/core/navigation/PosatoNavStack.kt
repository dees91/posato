package app.posato.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import app.posato.core.designsystem.platformUsesCupertinoChrome

/**
 * One destination's screen stack on Navigation 3. The stack is derived from the screen's own state, and
 * [onBack] is the top screen's explicit Back action, so the system back gesture, the Mac back command,
 * and the visible Back button share one path. Each screen is opaque, so a sliding screen never shows another
 * through it. While [backEnabled] is false the stack's back dispatcher
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
    val surface = MaterialTheme.colorScheme.surface
    val entryProvider = { key: T ->
        NavEntry(key) {
            Box(Modifier.fillMaxSize().background(surface), contentAlignment = contentAlignment) { content(key) }
        }
    }
    val dispatcher = rememberStackDispatcherOwner(enabled = backEnabled)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcher) {
        if (platformUsesCupertinoChrome) {
            CupertinoNavDisplay(backStack, onBack, platformReducesMotion(), modifier, contentAlignment, content)
        } else {
            PosatoNavDisplay(backStack, onBack, contentAlignment, entryProvider, clipped)
        }
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
 * A stack's own back dispatcher, a child of the enclosing one. Navigation 3 keeps a screen it removed as unused
 * movable content until the end of the next recomposition, so a stack nested in that screen can be forgotten
 * after an enclosing stack was disposed, which already disposed this dispatcher with it. Disposing it again
 * would throw, so it is disposed only while no enclosing stack has been.
 */
internal class StackDispatcherOwner(
    override val navigationEventDispatcher: NavigationEventDispatcher,
    private val enclosing: StackDispatcherOwner?,
) : NavigationEventDispatcherOwner {
    private var disposed = false

    private val isDisposed: Boolean
        get() {
            return disposed || enclosing?.isDisposed == true
        }

    fun dispose() {
        if (!isDisposed) {
            navigationEventDispatcher.dispose()
        }
        disposed = true
    }
}

@Composable
internal fun rememberStackDispatcherOwner(enabled: Boolean): StackDispatcherOwner {
    val parent = checkNotNull(LocalNavigationEventDispatcherOwner.current) { "A screen stack needs an enclosing back dispatcher." }
    val owner = remember(parent) {
        // Only stacks and their screens provide a dispatcher below the root, so a parent is always one of those.
        StackDispatcherOwner(NavigationEventDispatcher(parent.navigationEventDispatcher), parent as? StackDispatcherOwner)
    }
    LaunchedEffect(owner, enabled) { owner.navigationEventDispatcher.isEnabled = enabled }
    DisposableEffect(owner) { onDispose { owner.dispose() } }
    return owner
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
