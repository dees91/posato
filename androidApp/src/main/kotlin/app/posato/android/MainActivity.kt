package app.posato.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.posato.openPosatoLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/** The window of Posato for Android. Sessions and schedules run in the guard service, so the window never hosts them. */
class MainActivity : ComponentActivity() {
    private var folderResult: CompletableDeferred<String?>? = null
    private var grantResult: CompletableDeferred<Unit>? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        folderResult?.complete(uri?.let(::treePath))
    }

    private var createResult: CompletableDeferred<Uri?>? = null
    private var openResult: CompletableDeferred<Uri?>? = null

    private val documentCreator = registerForActivityResult(ActivityResultContracts.CreateDocument(PAUSE_SET_FILE_TYPE)) { uri ->
        createResult?.complete(uri)
    }

    private val documentOpener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        openResult?.complete(uri)
    }

    private val grantScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        grantResult?.complete(Unit)
    }

    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        grantResult?.complete(Unit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Posato draws behind the status and navigation bars on every release, as Android 15 and later require; the
        // shared screens keep their content inside the safe drawing area.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as PosatoAndroidApp
        GuardService.start(this)
        lifecycleScope.launch { runCatching { app.runtime.preparePauseSets() } }
        if (savedInstanceState == null) intent?.dataString?.let(::openPosatoLink)
        setContent { app.runtime.application.Content(hostsSession = false) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let(::openPosatoLink)
    }

    /** The folder the person picks, as a file system path; only a folder on this phone's shared storage can be used. */
    suspend fun browseFolder(): String? {
        val result = CompletableDeferred<String?>()
        folderResult = result
        folderPicker.launch(null)
        return result.await()
    }

    /** Where the person saves an export, from the system's document picker. */
    suspend fun createDocument(fileName: String): Uri? {
        val result = CompletableDeferred<Uri?>()
        createResult = result
        documentCreator.launch(fileName)
        return result.await()
    }

    /** The file the person opens for an import; any type, since a file is recognized by its first bytes. */
    suspend fun openDocument(): Uri? {
        val result = CompletableDeferred<Uri?>()
        openResult = result
        documentOpener.launch(arrayOf("*/*"))
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
        const val PAUSE_SET_FILE_TYPE = "application/vnd.posato.pause-sets"
    }
}
