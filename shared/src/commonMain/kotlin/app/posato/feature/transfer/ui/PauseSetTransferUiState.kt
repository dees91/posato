package app.posato.feature.transfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.posato.feature.transfer.data.ExportCode
import app.posato.feature.transfer.data.PauseSetFilePorts
import app.posato.feature.transfer.data.PauseSetTransfer
import app.posato.feature.transfer.data.TransferImport
import app.posato.feature.transfer.data.TransferRead
import app.posato.feature.transfer.domain.ImportMode
import app.posato.feature.transfer.domain.PauseSetFileLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Export and import from the Pause sets menu, one dialog at a time. */
@Stable
internal class PauseSetTransferUiState(
    private val transfer: PauseSetTransfer,
    private val files: PauseSetFilePorts,
    private val linked: () -> Boolean,
    private val scope: CoroutineScope,
) {
    var dialog by mutableStateOf<TransferDialog?>(null)
        private set

    val available: Boolean
        get() = files.available

    fun close() {
        dialog = null
    }

    fun startExport() {
        scope.launch {
            val contents = transfer.exportContents()
            dialog = if (contents == null) failure("Your pause sets could not be read.") else TransferDialog.Export(contentsOf(contents), code = null)
        }
    }

    /** Turns the code on or off; a new code is drawn each time it is turned on. */
    fun protect(on: Boolean) {
        val export = dialog as? TransferDialog.Export ?: return
        dialog = export.copy(code = if (on) transfer.newCode() else null, failed = false)
    }

    fun export() {
        val export = dialog as? TransferDialog.Export ?: return
        if (export.busy) return
        dialog = export.copy(busy = true, failed = false)
        scope.launch {
            val file = transfer.export(export.code)
            val saved = file != null && files.export(file.bytes, EXPORT_FILE_NAME)
            dialog = when {
                saved && export.code != null -> TransferDialog.Message(
                    "Exported",
                    "Send the code ${ExportCode.grouped(export.code)} separately from the file. Posato does not keep it.",
                )
                saved -> null
                file == null -> export.copy(busy = false, failed = true)
                else -> export.copy(busy = false)
            }
        }
    }

    fun startImport() {
        scope.launch {
            val bytes = files.pick(PauseSetFileLimits.MAX_FILE_BYTES) ?: return@launch
            if (bytes.size > PauseSetFileLimits.MAX_FILE_BYTES) {
                dialog = failure("This file is too large to be a Posato export.")
                return@launch
            }
            open(bytes, code = null)
        }
    }

    fun enterCode(code: String) {
        val entry = dialog as? TransferDialog.Code ?: return
        if (entry.busy) return
        dialog = entry.copy(busy = true, failure = null)
        scope.launch { open(entry.file, code) }
    }

    fun choose(mode: ImportMode) {
        val review = dialog as? TransferDialog.Review ?: return
        if (review.busy || review.preview.mode == mode) return
        scope.launch {
            val preview = transfer.preview(review.preview.file, mode, review.preview.ids)
            dialog = if (preview == null) failure("Your pause sets could not be read.") else review.copy(preview = preview, notice = null)
        }
    }

    fun import() {
        val review = dialog as? TransferDialog.Review ?: return
        if (review.busy) return
        dialog = review.copy(busy = true)
        scope.launch {
            dialog = when (val result = transfer.import(review.preview)) {
                is TransferImport.Imported -> TransferDialog.Result(result.summary, result.schedules, transfer.openNow(result.schedules))
                is TransferImport.Changed -> review.copy(
                    preview = result.preview,
                    busy = false,
                    notice = "Your pause sets changed since you opened the file. Check what the import does now.",
                )
                TransferImport.Failed -> review.copy(busy = false, notice = "The import could not be saved. Nothing was changed.")
            }
        }
    }

    fun turnOnSchedules() {
        val result = dialog as? TransferDialog.Result ?: return
        if (result.busy) return
        dialog = result.copy(busy = true, failed = false)
        scope.launch {
            val done = transfer.turnOn(result.schedules)
            dialog = result.copy(busy = false, turnedOn = done, failed = !done)
        }
    }

    private suspend fun open(
        bytes: ByteArray,
        code: String?,
    ) {
        dialog = when (val read = transfer.read(bytes, code)) {
            is TransferRead.File -> transfer.preview(read.file, ImportMode.COMBINE)
                ?.let { preview -> TransferDialog.Review(preview, linked()) }
                ?: failure("Your pause sets could not be read.")
            TransferRead.CodeNeeded -> TransferDialog.Code(bytes)
            TransferRead.CodeMismatch -> TransferDialog.Code(bytes, failure = "This code doesn't match, or the file was changed.")
            TransferRead.Damaged -> failure("This file is damaged. Export it again on the other device.")
            TransferRead.NewerVersion -> failure("This file was made by a newer Posato. Update Posato on this device, then try again.")
            TransferRead.NotPosatoFile -> failure("This is not a Posato export. Choose a .posato file.")
            TransferRead.InvalidContents -> failure("This file has a set, website, or schedule Posato cannot use.")
            TransferRead.OverLimit -> failure("This file holds more sets, websites, or schedules than Posato keeps.")
        }
    }

    private fun failure(text: String): TransferDialog {
        return TransferDialog.Message("Import", text)
    }
}

@Composable
internal fun rememberPauseSetTransferUiState(
    transfer: PauseSetTransfer,
    files: PauseSetFilePorts,
    linked: () -> Boolean,
): PauseSetTransferUiState {
    val scope = rememberCoroutineScope()
    return remember(transfer, files) { PauseSetTransferUiState(transfer, files, linked, scope) }
}

private const val EXPORT_FILE_NAME: String = "Posato pause sets.posato"
