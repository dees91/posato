package app.posato

import androidx.compose.ui.window.ComposeUIViewController
import app.posato.di.createIosApplicationRuntime
import app.posato.feature.sync.data.IosCryptoProvider
import platform.UIKit.UIViewController

fun mainViewController(cryptoProvider: IosCryptoProvider): UIViewController {
    val runtime = createIosApplicationRuntime(cryptoProvider)

    return ComposeUIViewController {
        runtime.applicationGraph.application.Content()
    }
}
