package app.posato.prototype

import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.ui.PosatoPrototypeApp
import platform.UIKit.UIViewController

fun prototypeViewController(): UIViewController {
    return ComposeUIViewController {
        val prototypeViewModel = viewModel { PrototypeViewModel(PrototypePlatform.IPhone) }
        PosatoPrototypeApp(prototypeViewModel)
    }
}
