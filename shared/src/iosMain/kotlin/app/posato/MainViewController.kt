package app.posato

import androidx.compose.ui.window.ComposeUIViewController
import app.posato.di.createIosApplicationGraph
import platform.UIKit.UIViewController

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    val applicationGraph = createIosApplicationGraph()

    return ComposeUIViewController {
        applicationGraph.application.Content()
    }
}
