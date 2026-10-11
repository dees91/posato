package app.posato.android

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import app.posato.feature.targets.data.ApplicationCatalog
import app.posato.feature.targets.data.CatalogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The apps on the home screen, by package. The launcher, Settings, the phone app, and Posato are never offered, so a
 * pause cannot cover what the person needs to leave it or to call.
 */
internal class LauncherCatalog(
    private val app: PosatoAndroidApp,
) : ApplicationCatalog {
    override suspend fun installed(): List<CatalogEntry> {
        return withContext(Dispatchers.IO) {
            val manager = app.packageManager
            val protected = protectedPackages()
            manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(manager).toString() }
                .filter { (name, _) -> name !in protected }
                .distinctBy { it.first }
                .map { (name, label) -> CatalogEntry(name, label, name) }
                .sortedBy { it.name.lowercase() }
        }
    }

    override suspend fun choose(
        entries: List<CatalogEntry>,
        chosen: Set<String>,
    ): List<CatalogEntry>? {
        val activity = app.activity ?: return null
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val checked = BooleanArray(entries.size) { entries[it].key in chosen }
                AlertDialog.Builder(activity)
                    .setTitle("Choose apps")
                    .setMultiChoiceItems(entries.map { it.name }.toTypedArray(), checked) { _, index, isChecked -> checked[index] = isChecked }
                    .setPositiveButton("Choose") { _, _ -> continuation.resume(entries.filterIndexed { index, _ -> checked[index] }) }
                    .setNegativeButton("Cancel") { _, _ -> continuation.resume(null) }
                    .setOnCancelListener { if (continuation.isActive) continuation.resume(null) }
                    .show()
            }
        }
    }

    private fun protectedPackages(): Set<String> {
        val manager = app.packageManager
        val home = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }
        val settings = manager.queryIntentActivities(Intent(Settings.ACTION_SETTINGS), 0).map { it.activityInfo.packageName }
        val dialer = app.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        return (home + settings + listOfNotNull(dialer, app.packageName)).toSet()
    }
}
