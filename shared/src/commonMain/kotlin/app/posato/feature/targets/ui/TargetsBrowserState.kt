package app.posato.feature.targets.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class TargetsCategory { WEBSITES, APPLICATIONS }

internal class TargetsBrowserState {
    var category: TargetsCategory by mutableStateOf(TargetsCategory.WEBSITES)
    var searching: Boolean by mutableStateOf(false)
    var showingWebsiteEditor: Boolean by mutableStateOf(true)
    val search = TextFieldState()
    val websiteDraft = TextFieldState()
    val websitesScroll = LazyListState()
    val applicationsScroll = LazyListState()
    private var nextSubmissionId: Long = 0
    private var pendingSubmission: PendingWebsiteSubmission? = null
    private var editingSession: Long? = null
    private var editingDraft = TextFieldState()
    private var receipt: WebsiteBatchReceipt? by mutableStateOf(null)
    private var receiptDraft: String = ""
    val lastReceipt: WebsiteBatchReceipt?
        get() {
            return receipt.takeIf { websiteDraft.text.toString() == receiptDraft }
        }

    fun editorDraft(
        session: Long,
        initial: String
    ): TextFieldState {
        if (editingSession != session) {
            editingSession = session
            editingDraft = TextFieldState(initial)
        }
        return editingDraft
    }

    fun submit(onSubmit: (String, Long) -> Unit) {
        val input = websiteDraft.text.toString()
        if (input.isBlank() || pendingSubmission != null) {
            return
        }
        nextSubmissionId++
        pendingSubmission = PendingWebsiteSubmission(nextSubmissionId, input)
        receipt = null
        onSubmit(input, nextSubmissionId)
    }

    fun accept(receipt: WebsiteBatchReceipt?) {
        val pending = pendingSubmission ?: return
        if (receipt == null || receipt.submissionId != pending.id) {
            return
        }
        pendingSubmission = null
        if (websiteDraft.text.toString() != pending.input) {
            return
        }
        this.receipt = receipt
        if (receipt.saved) {
            val entries = websiteBatchEntries(pending.input)
            val remainder = receipt.rejectedIndices.joinToString("\n") { entries[it] }
            websiteDraft.edit { replace(0, length, remainder) }
        }
        receiptDraft = websiteDraft.text.toString()
    }
}

private class PendingWebsiteSubmission(
    val id: Long,
    val input: String
) {
    override fun toString(): String {
        return "PendingWebsiteSubmission(redacted)"
    }
}
