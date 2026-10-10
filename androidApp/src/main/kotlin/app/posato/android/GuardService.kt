package app.posato.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The foreground service of ADR 0010 on Android. It keeps Posato running so sessions, schedules, and the folder
 * workspace work with the app closed; while a pause runs it watches which app is in front and covers a chosen one with
 * the block screen, and it clears the pause by itself at its end.
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastBlockMillis = 0L

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val runtime = (application as PosatoAndroidApp).runtime
        scope.launch { runtime.host() }
        scope.launch { watch() }
        scope.launch {
            while (isActive) {
                scheduleWake(this@GuardService)
                delay(WAKE_INTERVAL_MILLIS)
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watch() {
        val state = PauseState(this)
        val usage = getSystemService(UsageStatsManager::class.java)
        while (scope.isActive) {
            val pause = state.applied()
            if (pause != null && System.currentTimeMillis() >= pause.endEpochMillis) {
                state.clear(expired = true)
                DnsVpnService.refresh(this)
            } else if (pause != null && pause.packages.isNotEmpty()) {
                blockIfInFront(usage, pause.packages)
            }
            delay(WATCH_MILLIS)
        }
    }

    private fun blockIfInFront(
        usage: UsageStatsManager,
        packages: Set<String>,
    ) {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - LOOKBACK_MILLIS, now)
        val event = UsageEvents.Event()
        var front: String? = null
        var frontAt = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                front = event.packageName
                frontAt = event.timeStamp
            }
        }
        if (front != null && front in packages && frontAt > lastBlockMillis) {
            lastBlockMillis = now
            startActivity(Intent(this, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Posato", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Posato is ready")
            .setContentText("Pauses and schedules work while the app is closed.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL = "posato-guard"
        private const val WATCH_MILLIS = 500L
        private const val LOOKBACK_MILLIS = 10_000L
        private const val WAKE_INTERVAL_MILLIS = 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GuardService::class.java))
        }

        /** An exact alarm a minute ahead, so schedules and expiries are evaluated on time even while the phone idles. */
        fun scheduleWake(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val wake = PendingIntent.getBroadcast(context, 0, Intent(context, WakeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
            val at = SystemClock.elapsedRealtime() + WAKE_INTERVAL_MILLIS
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, wake)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, wake)
            }
        }
    }
}

/** Starts the guard after a reboot and on each wake alarm. */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        GuardService.start(context)
        val runtime = (context.applicationContext as PosatoAndroidApp).runtime
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runtime.wake()
            } finally {
                pending.finish()
            }
        }
    }
}
