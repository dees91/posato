package app.posato.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/** The window of Posato for Android. Sessions and schedules run in the guard service, so the window never hosts them. */
class MainActivity : ComponentActivity() {
    private var folderResult: CompletableDeferred<String?>? = null
    private var grantResult: CompletableDeferred<Unit>? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        folderResult?.complete(uri?.let(::treePath))
    }

    private val grantScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        grantResult?.complete(Unit)
    }

    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        grantResult?.complete(Unit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PosatoAndroidApp
        GuardService.start(this)
        lifecycleScope.launch { runCatching { app.runtime.preparePauseSets() } }
        setContent { app.runtime.application.Content(hostsSession = false) }
    }

    /** The folder the person picks, as a file system path; only a folder on this phone's shared storage can be used. */
    suspend fun browseFolder(): String? {
        val result = CompletableDeferred<String?>()
        folderResult = result
        folderPicker.launch(null)
        return result.await()
    }

    /** Opens one grant screen and returns when the person comes back. */
    suspend fun openGrant(intent: Intent) {
        val result = CompletableDeferred<Unit>()
        grantResult = result
        grantScreen.launch(intent)
        result.await()
    }

    suspend fun requestNotifications() {
        val result = CompletableDeferred<Unit>()
        grantResult = result
        notificationRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        result.await()
    }

    private fun treePath(uri: Uri): String? {
        val document = DocumentsContract.getTreeDocumentId(uri)
        val volume = document.substringBefore(':')
        val relative = document.substringAfter(':', "")
        return if (volume == PRIMARY) Environment.getExternalStorageDirectory().resolve(relative).absolutePath else null
    }

    private companion object {
        const val PRIMARY = "primary"
    }
}
