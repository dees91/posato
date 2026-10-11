package app.posato.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.posato.core.database.PosatoDatabase
import app.posato.di.AndroidApplicationRuntime
import app.posato.di.createAndroidApplicationRuntime
import app.posato.feature.notifications.UnavailableSessionNotifications
import app.posato.feature.targets.data.CatalogApplicationMappings
import kotlinx.coroutines.Dispatchers
import java.lang.ref.WeakReference

/** Builds the shared graph once per process and remembers the activity in front, which pickers and grants need. */
class PosatoAndroidApp : Application() {
    private var front: WeakReference<MainActivity>? = null

    val activity: MainActivity?
        get() = front?.get()

    val runtime: AndroidApplicationRuntime by lazy {
        val mappings = CatalogApplicationMappings(filesDir.toPath().resolve("application-choices"), LauncherCatalog(this), Dispatchers.IO)
        createAndroidApplicationRuntime(
            applicationMappings = mappings,
            enforcement = AndroidEnforcement(this, PauseState(this), mappings),
            database = PosatoDatabase(AndroidSqliteDriver(PosatoDatabase.Schema.synchronous(), this, "posato-policy.db")),
            notifications = UnavailableSessionNotifications,
            applicationAccess = AndroidAccess(this),
            localDirectory = filesDir.resolve("sync").absolutePath,
            browseFolder = { activity?.browseFolder() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (activity is MainActivity) front = WeakReference(activity)
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
