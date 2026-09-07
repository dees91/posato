package app.posato.prototype.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Posato · Component Catalog",
            state = rememberWindowState(width = 1200.dp, height = 900.dp),
        ) {
            var state by remember { mutableStateOf(CatalogState()) }
            var lastAction by remember { mutableStateOf("Choose a component to explore its behavior.") }
            PosatoComponentCatalog(
                modifier = Modifier.fillMaxSize(),
                state = state,
                onStateChange = { state = it },
                onAction = { lastAction = it },
                lastAction = lastAction,
            )
        }
    }
}
