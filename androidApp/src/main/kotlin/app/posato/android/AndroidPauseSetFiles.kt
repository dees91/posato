package app.posato.android

import app.posato.feature.transfer.data.PauseSetFilePorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Export and import through the system's document picker: a file the person names and places, or opens. */
internal class AndroidPauseSetFiles(
    private val activity: () -> MainActivity?,
) : PauseSetFilePorts {
    override suspend fun export(
        bytes: ByteArray,
        fileName: String,
    ): Boolean {
        val front = activity() ?: return false
        val uri = front.createDocument(fileName) ?: return false
        return withContext(Dispatchers.IO) {
            try {
                front.contentResolver.openOutputStream(uri, "wt")?.use { stream -> stream.write(bytes) } != null
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    override suspend fun pick(limit: Int): ByteArray? {
        val front = activity() ?: return null
        val uri = front.openDocument() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                front.contentResolver.openInputStream(uri)?.use { stream -> stream.readNBytes(limit + 1) }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }
}
