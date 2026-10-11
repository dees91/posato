package app.posato.android

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.ApplicationAccessResult
import app.posato.feature.targets.data.LocalApplicationMappingsAccess

/**
 * Onboarding's access step on Android: each press opens the next missing grant (VPN, usage access, display over other
 * apps, notifications, exact alarms, all-files access for the sync folder, and the battery exemption).
 */
internal class AndroidAccess(
    private val app: PosatoAndroidApp,
) : ApplicationAccessPort {
    override suspend fun requestAuthorization(): ApplicationAccessResult {
        val activity = app.activity ?: return ApplicationAccessResult.Failed
        val packageUri = Uri.parse("package:${app.packageName}")
        when {
            !Permissions.vpn(app) -> VpnService.prepare(app)?.let { activity.openGrant(it) }
            !Permissions.usageAccess(app) -> activity.openGrant(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            !Permissions.overlay(app) -> activity.openGrant(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
            !Permissions.notifications(app) -> activity.requestNotifications()
            !Permissions.exactAlarms(app) -> activity.openGrant(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
            !Permissions.allFiles() -> activity.openGrant(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri))
        }
        val access = if (Permissions.all(app)) LocalApplicationMappingsAccess.READY else LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED
        return ApplicationAccessResult.Determined(access)
    }
}
