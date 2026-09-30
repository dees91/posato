package app.posato.feature.sync.bootstrap

import platform.Foundation.NSLock
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

internal object IosSyncBackgroundTime : SyncBackgroundTime {
    override fun begin(): AutoCloseable {
        return IosBackgroundHold(UIApplication.sharedApplication)
    }
}

private class IosBackgroundHold(
    private val application: UIApplication,
) : AutoCloseable {
    private val lock = NSLock()
    private var identifier: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var ended = false

    init {
        val started = application.beginBackgroundTaskWithName(TASK_NAME) { close() }
        lock.lock()
        try {
            if (ended) {
                application.endBackgroundTask(started)
            } else {
                identifier = started
            }
        } finally {
            lock.unlock()
        }
    }

    override fun close() {
        lock.lock()
        val held = identifier
        try {
            ended = true
            identifier = UIBackgroundTaskInvalid
        } finally {
            lock.unlock()
        }
        if (held != UIBackgroundTaskInvalid) {
            application.endBackgroundTask(held)
        }
    }

    private companion object {
        const val TASK_NAME = "Posato sync"
    }
}
