package app.posato

import androidx.compose.ui.window.ComposeUIViewController
import app.posato.di.createIosApplicationRuntime
import app.posato.feature.enforcement.IosEnforcementProvider
import app.posato.feature.enforcement.IosSuspendedExpiryProvider
import app.posato.feature.sync.data.IosCloudKitMailboxProvider
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosKeychainProvider
import app.posato.feature.sync.data.UnavailableIosKeychainProvider
import app.posato.feature.targets.data.IosApplicationMappingsProvider
import platform.UIKit.UIViewController

fun mainViewController(
    cryptoProvider: IosCryptoProvider,
    applicationMappingsProvider: IosApplicationMappingsProvider,
    enforcementProvider: IosEnforcementProvider,
    suspendedExpiryProvider: IosSuspendedExpiryProvider,
    keychainProvider: IosKeychainProvider?,
    mailboxProvider: IosCloudKitMailboxProvider,
): UIViewController {
    val runtime = createIosApplicationRuntime(
        cryptoProvider,
        applicationMappingsProvider,
        enforcementProvider,
        suspendedExpiryProvider,
        keychainProvider ?: UnavailableIosKeychainProvider,
        mailboxProvider,
    )

    return ComposeUIViewController {
        runtime.applicationGraph.application.Content()
    }
}
