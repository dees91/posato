package app.posato.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeWindow
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

@Composable
internal fun WindowChrome(
    window: ComposeWindow,
    fullscreen: Boolean,
    onContrastChange: (Boolean) -> Unit,
) {
    val updateContrast by rememberUpdatedState(onContrastChange)
    DisposableEffect(window, fullscreen) {
        val configure = {
            updateContrast(MacWindow.highContrast())
            MacWindow.configure(window.windowHandle, fullscreen)
            window.revalidate()
            window.repaint()
        }
        val listener = object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) {
                configure()
            }

            override fun windowActivated(event: WindowEvent) {
                updateContrast(MacWindow.highContrast())
            }
        }
        window.addWindowListener(listener)
        if (window.isShowing) configure()
        onDispose { window.removeWindowListener(listener) }
    }
}

internal object MacWindow {
    init {
        val resourcesDirectory = checkNotNull(System.getProperty("compose.application.resources.dir"))
        val library = File(resourcesDirectory, "native/libPosatoWindow.dylib")
        System.load(library.absolutePath)
    }

    external fun configure(
        windowHandle: Long,
        fullscreen: Boolean
    )

    external fun highContrast(): Boolean
}
