package app.posato

import androidx.compose.ui.window.ComposeUIViewController
import app.posato.di.createIosApplicationRuntime
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.targets.data.IosApplicationMappingsProvider
import platform.UIKit.UIViewController

fun mainViewController(
    cryptoProvider: IosCryptoProvider,
    applicationMappingsProvider: IosApplicationMappingsProvider,
): UIViewController {
    val runtime = createIosApplicationRuntime(cryptoProvider, applicationMappingsProvider)

    return ComposeUIViewController {
        runtime.applicationGraph.application.Content()
    }
}
