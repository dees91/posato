package app.posato

import androidx.compose.ui.window.ComposeUIViewController
import app.posato.di.createIosApplicationRuntime
import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementProvider
import app.posato.feature.enforcement.IosSuspendedExpiryProvider
import app.posato.feature.notifications.SessionNotificationPlatform
import app.posato.feature.schedules.IosScheduleBridge
import app.posato.feature.schedules.IosScheduleMonitorProvider
import app.posato.feature.sync.data.IosCloudKitMailboxProvider
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosKeychainProvider
import app.posato.feature.sync.data.UnavailableIosKeychainProvider
import app.posato.feature.targets.data.IosApplicationMappingsProvider
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController

fun mainViewController(
    cryptoProvider: IosCryptoProvider,
    applicationMappingsProvider: IosApplicationMappingsProvider,
    enforcementProvider: IosEnforcementProvider,
    suspendedExpiryProvider: IosSuspendedExpiryProvider,
    keychainProvider: IosKeychainProvider?,
    mailboxProvider: IosCloudKitMailboxProvider,
    notificationProvider: SessionNotificationPlatform,
    scheduleEnforcementProvider: IosEnforcementProvider,
    scheduleMonitorProvider: IosScheduleMonitorProvider,
): UIViewController {
    val runtime = createIosApplicationRuntime(
        cryptoProvider,
        applicationMappingsProvider,
        enforcementProvider,
        suspendedExpiryProvider,
        keychainProvider ?: UnavailableIosKeychainProvider,
        mailboxProvider,
        notificationProvider,
        IosScheduleBridge(IosEnforcement(scheduleEnforcementProvider), scheduleMonitorProvider),
    )

    // The one-time pause set upgrade runs before any host starts; a failure stays pending for the next launch.
    runCatching { runBlocking { runtime.pauseSetPreparation.prepare { emptyList() } } }
    return ComposeUIViewController {
        runtime.applicationGraph.application.Content()
    }
}
