package app.posato.android

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Environment
import android.os.Process
import android.provider.Settings

/** The grants a pause needs on Android, each checked from the system rather than remembered. */
internal object Permissions {
    fun usageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, null) ==
            AppOpsManager.MODE_ALLOWED
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

    /** What applying a pause needs; notifications, exact alarms, and file access only make it more reliable. */
    fun blockingReady(context: Context): Boolean {
        return usageAccess(context) && overlay(context) && vpn(context)
    }

    fun all(context: Context): Boolean {
        return blockingReady(context) && notifications(context) && exactAlarms(context) && allFiles()
    }
}
