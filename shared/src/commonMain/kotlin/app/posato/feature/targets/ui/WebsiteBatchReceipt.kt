package app.posato.feature.targets.ui

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class WebsiteBatchReceipt(
    val submissionId: Long,
    val saved: Boolean,
    val addedCount: Int = 0,
    val duplicateCount: Int = 0,
    val rejectedIndices: PersistentList<Int> = persistentListOf(),
    val tooLong: Boolean = false,
)
