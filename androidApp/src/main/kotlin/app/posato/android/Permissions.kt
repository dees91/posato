package app.posato.android

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** The grants a pause needs on Android, each checked from the system rather than remembered. */
internal object Permissions {
    fun usageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val op = AppOpsManager.OPSTR_GET_USAGE_STATS
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            appOps.checkOpNoThrow(op, Process.myUid(), context.packageName, null)
        } else {
            usageAccessModeBeforeBaklava(appOps, op, context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * The overload with an attribution tag arrived in Android 16 and replaced this one, so Android 13 to 15 can only
     * check the grant through the replaced call (approved exception, 2026-10-20).
     */
    @Suppress("DEPRECATION")
    private fun usageAccessModeBeforeBaklava(
        appOps: AppOpsManager,
        op: String,
        packageName: String
    ): Int {
        return appOps.unsafeCheckOpNoThrow(op, Process.myUid(), packageName)
    }

    fun overlay(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun vpn(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    fun notifications(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun exactAlarms(context: Context): Boolean {
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun allFiles(): Boolean {
        return Environment.isExternalStorageManager()
    }

    /** Whether Doze leaves Posato's network and wake alarms alone, which background sync and schedules rely on. */
    fun batteryExempt(context: Context): Boolean {
        return context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * What applying a pause needs; notifications, exact alarms, file access, and the battery exemption only make it
     * more reliable.
     */
    fun blockingReady(context: Context): Boolean {
        return usageAccess(context) && overlay(context) && vpn(context)
    }

    fun all(context: Context): Boolean {
        return blockingReady(context) && notifications(context) && exactAlarms(context) && allFiles() && batteryExempt(context)
    }
}
