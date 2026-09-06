package app.posato.prototype

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.ui.PosatoPrototypeApp
import app.posato.prototype.ui.PrototypeUiTokens
import app.posato.prototype.ui.rememberPrototypeControlsState
import java.awt.Dimension

fun main() {
    application {
        val controls = rememberPrototypeControlsState()
        val windowState = rememberWindowState(width = PrototypeUiTokens.DesktopWidth, height = PrototypeUiTokens.DesktopHeight)
        Window(
            onCloseRequest = ::exitApplication,
            title = "Posato Prototype",
            state = windowState,
            onPreviewKeyEvent = { event ->
                val shortcut = event.key == Key.P && event.isMetaPressed && event.isShiftPressed && event.type == KeyEventType.KeyDown
                if (shortcut) controls.isOpen = !controls.isOpen
                shortcut
            },
        ) {
            PrototypeWindowChrome(window, fullscreen = windowState.placement == WindowPlacement.Fullscreen)
            SideEffect {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                window.minimumSize = Dimension((PosatoSize.NavigationSidebar + PosatoSize.Phone).value.toInt(), 0)
            }
            val prototypeViewModel = viewModel { PrototypeViewModel(PrototypePlatform.Mac) }
            PosatoPrototypeApp(prototypeViewModel, controls = controls)
        }
    }
}
