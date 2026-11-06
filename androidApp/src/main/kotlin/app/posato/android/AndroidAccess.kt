package app.posato.android

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import androidx.annotation.StringRes
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.ApplicationAccessResult
import app.posato.feature.targets.data.LocalApplicationMappingsAccess

/**
 * Onboarding's access step on Android: each press opens the next missing grant (VPN, usage access, display over other
 * apps, notifications, and exact alarms). Usage access and the overlay are explained first, and
 * a person who declines that explanation is not taken to Settings.
 */
internal class AndroidAccess(
    private val app: PosatoAndroidApp,
) : ApplicationAccessPort {
    override suspend fun requestAuthorization(): ApplicationAccessResult {
        val activity = app.activity ?: return ApplicationAccessResult.Failed
        val packageUri = Uri.parse("package:${app.packageName}")
        when {
            !Permissions.vpn(app) -> VpnService.prepare(app)?.let { activity.openGrant(it) }
            !Permissions.usageAccess(app) -> activity.openExplainedGrant(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), usageAccess)
            !Permissions.overlay(app) -> activity.openExplainedGrant(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri), overlay)
            !Permissions.notifications(app) -> activity.requestNotifications()
            !Permissions.exactAlarms(app) -> activity.openGrant(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
        }
        val access = if (Permissions.all(app)) LocalApplicationMappingsAccess.READY else LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED
        return ApplicationAccessResult.Determined(access)
    }

    /** Every grant already given reads as allowed; a missing one is left to the person's press, which asks for it. */
    override suspend fun currentAccess(): ApplicationAccessResult? {
        return ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.READY).takeIf { Permissions.all(app) }
    }
}

/** The title and message that explain one Settings grant before Android opens it. */
private class Disclosure(
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val usageAccess = Disclosure(R.string.disclosure_usage_title, R.string.disclosure_usage_body)
private val overlay = Disclosure(R.string.disclosure_overlay_title, R.string.disclosure_overlay_body)

private suspend fun MainActivity.openExplainedGrant(
    intent: Intent,
    disclosure: Disclosure,
) {
    if (disclose(disclosure.title, disclosure.body)) openGrant(intent)
}
