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

    fun pickFile(completion: (NSData?) -> Unit)
}

internal object NoPauseSetFilePicker : IosPauseSetFilePicker {
    override fun exportFile(
        data: NSData,
        fileName: String,
        completion: (Boolean) -> Unit,
    ) {
        completion(false)
    }

    override fun pickFile(completion: (NSData?) -> Unit) {
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
            suspendCancellableCoroutine { continuation -> picker.exportFile(data, fileName) { done -> continuation.resume(done) } }
        }
    }

    override suspend fun pick(limit: Int): ByteArray? {
        val data = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<NSData?> { continuation -> picker.pickFile { file -> continuation.resume(file) } }
        } ?: return null
        // Only the first bytes past the limit are kept, so a file that is too large is refused by its size.
        return data.toByteArray().let { bytes -> if (bytes.size > limit + 1) bytes.copyOf(limit + 1) else bytes }
    }
}
