package app.posato.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.posato.desktop.mappings.DesktopLocalApplicationMappings
import app.posato.di.createDesktopApplicationGraph

fun main() {
    DesktopLocalApplicationMappings().use { applicationMappings ->
        val applicationGraph = createDesktopApplicationGraph(applicationMappings)

        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Posato",
            ) {
                applicationGraph.application.Content()
            }
        }
    }
}
