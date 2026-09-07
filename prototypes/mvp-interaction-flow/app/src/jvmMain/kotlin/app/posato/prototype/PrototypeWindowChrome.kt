package app.posato.prototype

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.awt.ComposeWindow
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

@Composable
internal fun PrototypeWindowChrome(
    window: ComposeWindow,
    fullscreen: Boolean
) {
    DisposableEffect(window, fullscreen) {
        val configure = {
            PrototypeMacWindow.configure(window.windowHandle, fullscreen)
            window.revalidate()
            window.repaint()
        }
        val listener = object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) {
                configure()
            }
        }
        window.addWindowListener(listener)
        if (window.isShowing) configure()
        onDispose { window.removeWindowListener(listener) }
    }
}

internal object PrototypeMacWindow {
    init {
        val resourcesDirectory = checkNotNull(System.getProperty("compose.application.resources.dir"))
        val library = File(resourcesDirectory, "native/libPosatoPrototypeWindow.dylib")
        System.load(library.absolutePath)
    }

    external fun configure(
        windowHandle: Long,
        fullscreen: Boolean
    )
}
