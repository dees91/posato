package app.posato.feature.transfer

import app.posato.feature.sync.folder.toByteArray
import app.posato.feature.sync.folder.toData
import app.posato.feature.transfer.data.PauseSetFilePorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import kotlin.coroutines.resume

/**
 * The iPhone's share sheet and document picker, presented by the app's Swift side: an export is shared as a
 * temporary file that is deleted when the sheet closes, and an import is a copy of the chosen file.
 */
interface IosPauseSetFilePicker {
    fun exportFile(
        data: NSData,
        fileName: String,
        completion: (Boolean) -> Unit,
    )

    /** Reads at most [maxBytes] of the chosen file, so a file that is too large is refused by its size alone. */
    fun pickFile(
        maxBytes: Int,
        completion: (NSData?) -> Unit,
    )
}

internal object NoPauseSetFilePicker : IosPauseSetFilePicker {
    override fun exportFile(
        data: NSData,
        fileName: String,
        completion: (Boolean) -> Unit,
    ) {
        completion(false)
    }

    override fun pickFile(
        maxBytes: Int,
        completion: (NSData?) -> Unit,
    ) {
        completion(null)
    }
}

internal class IosPauseSetFiles(
    private val picker: IosPauseSetFilePicker,
) : PauseSetFilePorts {
    override val available: Boolean
        get() = picker !== NoPauseSetFilePicker

    override suspend fun export(
        bytes: ByteArray,
        fileName: String,
    ): Boolean {
        val data = bytes.toData()
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                picker.exportFile(data, fileName) { done -> if (continuation.isActive) continuation.resume(done) }
            }
        }
    }

    override suspend fun pick(limit: Int): ByteArray? {
        val data = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<NSData?> { continuation ->
                picker.pickFile(limit + 1) { file -> if (continuation.isActive) continuation.resume(file) }
            }
        } ?: return null
        return data.toByteArray()
    }
}
