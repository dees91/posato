package app.posato

import androidx.compose.ui.window.ComposeUIViewController
import app.posato.di.createIosApplicationGraph
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val applicationGraph = createIosApplicationGraph()

    return ComposeUIViewController {
        applicationGraph.application.Content()
    }
}
