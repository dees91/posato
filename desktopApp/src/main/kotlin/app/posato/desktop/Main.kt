package app.posato.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.posato.di.createDesktopApplicationGraph

fun main() {
    val applicationGraph = createDesktopApplicationGraph()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Posato",
        ) {
            applicationGraph.application.Content()
        }
    }
}
