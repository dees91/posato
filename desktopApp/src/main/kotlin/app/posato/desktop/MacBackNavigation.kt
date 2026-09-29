package app.posato.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import java.awt.EventQueue
import java.io.File

/**
 * The Mac's back commands: Command-[ as in Finder and Safari, and the trackpad's swipe between pages,
 * which AppKit tracks and this input turns into a back gesture that follows the fingers. Escape
 * already reaches the same dispatcher through Compose. Each returns one screen where a screen
 * offers Back; the native swipe starts only while one does.
 */
internal class MacBackInput : NavigationEventInput() {
    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        MacBackGesture.setAvailable(hasEnabledHandlers)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val command = event.isMetaPressed && !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed
        if (!command || event.key != Key.LeftBracket) return false
        if (event.type == KeyEventType.KeyDown) dispatchOnBackCompleted()
        return true
    }

    fun onSwipe(
        phase: Int,
        amount: Float,
    ) {
        val event = NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = amount.coerceIn(0f, 1f))
        when (phase) {
            SWIPE_STARTED -> dispatchOnBackStarted(event)
            SWIPE_CHANGED -> dispatchOnBackProgressed(event)
            SWIPE_COMPLETED -> dispatchOnBackCompleted()
            else -> dispatchOnBackCancelled()
        }
    }

    private companion object {
        const val SWIPE_STARTED = 0
        const val SWIPE_CHANGED = 1
        const val SWIPE_COMPLETED = 2
    }
}

@Composable
internal fun MacBackInputEffect(input: MacBackInput) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    DisposableEffect(dispatcher) {
        dispatcher?.addInput(input)
        MacBackGesture.attach(input)
        onDispose {
            MacBackGesture.detach(input)
            dispatcher?.removeInput(input)
        }
    }
}

internal object MacBackGesture {
    init {
        val resourcesDirectory = checkNotNull(System.getProperty("compose.application.resources.dir"))
        System.load(File(resourcesDirectory, "native/libPosatoWindow.dylib").absolutePath)
    }

    @Volatile
    private var input: MacBackInput? = null

    fun attach(target: MacBackInput) {
        input = target
        install()
    }

    fun detach(target: MacBackInput) {
        if (input === target) {
            input = null
            setAvailable(false)
        }
    }

    @JvmStatic
    fun onSwipe(
        phase: Int,
        amount: Float,
    ) {
        EventQueue.invokeLater { input?.onSwipe(phase, amount) }
    }

    external fun setAvailable(available: Boolean)

    private external fun install()
}
