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
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_import_word
import app.posato.generated.resources.transfer_changed_since
import app.posato.generated.resources.transfer_code_mismatch
import app.posato.generated.resources.transfer_damaged
import app.posato.generated.resources.transfer_exported
import app.posato.generated.resources.transfer_file_name
import app.posato.generated.resources.transfer_import_failed
import app.posato.generated.resources.transfer_invalid
import app.posato.generated.resources.transfer_newer
import app.posato.generated.resources.transfer_not_posato
import app.posato.generated.resources.transfer_over_limit
import app.posato.generated.resources.transfer_read_failed
import app.posato.generated.resources.transfer_send_code
import app.posato.generated.resources.transfer_too_large
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

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
            dialog = if (contents == null) failure(Res.string.transfer_read_failed) else TransferDialog.Export(contents, code = null)
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
            val saved = file != null && files.export(file.bytes, getString(Res.string.transfer_file_name))
            dialog = when {
                saved && export.code != null -> TransferDialog.Message(
                    Res.string.transfer_exported,
                    Res.string.transfer_send_code,
                    ExportCode.grouped(export.code),
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
                dialog = failure(Res.string.transfer_too_large)
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
            dialog = if (preview == null) failure(Res.string.transfer_read_failed) else review.copy(preview = preview, notice = null)
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
                    notice = Res.string.transfer_changed_since,
                )

                TransferImport.Failed -> review.copy(busy = false, notice = Res.string.transfer_import_failed)
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
                ?: failure(Res.string.transfer_read_failed)

            TransferRead.CodeNeeded -> TransferDialog.Code(bytes)

            TransferRead.CodeMismatch -> TransferDialog.Code(bytes, failure = Res.string.transfer_code_mismatch)

            TransferRead.Damaged -> failure(Res.string.transfer_damaged)

            TransferRead.NewerVersion -> failure(Res.string.transfer_newer)

            TransferRead.NotPosatoFile -> failure(Res.string.transfer_not_posato)

            TransferRead.InvalidContents -> failure(Res.string.transfer_invalid)

            TransferRead.OverLimit -> failure(Res.string.transfer_over_limit)
        }
    }

    private fun failure(text: StringResource): TransferDialog {
        return TransferDialog.Message(Res.string.action_import_word, text)
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
