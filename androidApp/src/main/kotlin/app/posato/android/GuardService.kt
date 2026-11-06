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
import app.posato.feature.enforcement.WebsiteWindowGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The foreground service of ADR 0010 on Android. It keeps Posato running so sessions, schedules, and the folder
 * workspace work with the app closed; while a pause runs it watches which app is in front and covers a chosen one with
 * the block screen, and it clears the pause by itself at its end.
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastBlockMillis = 0L
    private var queriedUntil = 0L
    private var front: String? = null
    private var frontAt = 0L
    private var windowWakeAt: Long? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // After a reboot a running website pause is only saved; start its tunnel again.
        DnsVpnService.refresh(this)
        val runtime = (application as PosatoAndroidApp).runtime
        scope.launch { runtime.host() }
        scope.launch { watch() }
        scope.launch {
            while (isActive) {
                scheduleWake(this@GuardService)
                delay(WAKE_INTERVAL_MILLIS)
            }
        }
        // A break ends at its exact instant even while the phone idles, not at the next minute's wake.
        scope.launch { runtime.nextBreakChange.collect { at -> scheduleBreakWake(this@GuardService, at) } }
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
            followWindows(pause)
            delay(WATCH_MILLIS)
        }
    }

    /**
     * Keeps an exact alarm at the next window boundary or segment end of the saved pause (TARGETS-013), as for break
     * boundaries, so the phone wakes there even while it idles; the VPN decides each name at query time.
     */
    private fun followWindows(pause: AppliedPause?) {
        val next = pause?.let {
            WebsiteWindowGate.nextBoundary(it.domains, it.windows, it.endEpochMillis, System.currentTimeMillis(), ZoneId.systemDefault())
        }
        if (next == windowWakeAt) return
        windowWakeAt = next
        scheduleExactWake(this, WINDOW_WAKE_REQUEST, next)
        if (pause != null) DnsVpnService.refresh(this)
    }

    /**
     * Follows the app in front across polls, so an app that was already open when a pause started is covered too; the
     * first poll looks back far enough to find it. Some phones, such as Samsung's, publish an event several seconds after
     * its time, so each poll also reads back over that delay and keeps the newest app that came to the front.
     */
    private fun blockIfInFront(
        usage: UsageStatsManager,
        packages: Set<String>,
    ) {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(if (queriedUntil == 0L) now - INITIAL_LOOKBACK_MILLIS else queriedUntil - LATE_EVENT_MILLIS, now)
        queriedUntil = now
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= frontAt) {
                front = event.packageName
                frontAt = event.timeStamp
            }
        }
        if (front in packages && frontAt > lastBlockMillis) {
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
            .setContentTitle(getString(R.string.guard_title))
            .setContentText(getString(R.string.guard_body))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL = "posato-guard"
        private const val WATCH_MILLIS = 500L
        private const val INITIAL_LOOKBACK_MILLIS = 3_600_000L
        private const val LATE_EVENT_MILLIS = 10_000L
        private const val WAKE_INTERVAL_MILLIS = 60_000L
        private const val BREAK_WAKE_REQUEST = 1
        private const val WINDOW_WAKE_REQUEST = 2

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GuardService::class.java))
        }

        /** An exact alarm at a break's start or end, replacing the previous one; none while no break runs. */
        fun scheduleBreakWake(
            context: Context,
            at: Long?,
        ) {
            scheduleExactWake(context, BREAK_WAKE_REQUEST, at)
        }

        /** An exact wall-clock alarm for [request], replacing its previous one; none when [at] is null. */
        private fun scheduleExactWake(
            context: Context,
            request: Int,
            at: Long?,
        ) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val wake = PendingIntent.getBroadcast(
                context,
                request,
                Intent(context, WakeReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarms.cancel(wake)
            if (at == null) {
                return
            }
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake)
            }
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
