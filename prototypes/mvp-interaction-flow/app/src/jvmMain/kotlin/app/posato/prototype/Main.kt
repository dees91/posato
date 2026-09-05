package app.posato.prototype

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.ui.PosatoPrototypeApp
import app.posato.prototype.ui.PrototypeUiTokens
import app.posato.prototype.ui.rememberPrototypeControlsState

fun main() {
    application {
        val controls = rememberPrototypeControlsState()
        Window(
            onCloseRequest = ::exitApplication,
            title = "Posato Prototype",
            state = rememberWindowState(width = PrototypeUiTokens.DesktopWidth, height = PrototypeUiTokens.DesktopHeight),
            onPreviewKeyEvent = { event ->
                val shortcut = event.key == Key.P && event.isMetaPressed && event.isShiftPressed && event.type == KeyEventType.KeyDown
                if (shortcut) controls.isOpen = !controls.isOpen
                shortcut
            },
        ) {
            val prototypeViewModel = viewModel { PrototypeViewModel(PrototypePlatform.Mac) }
            PosatoPrototypeApp(prototypeViewModel, controls = controls)
        }
    }
}
