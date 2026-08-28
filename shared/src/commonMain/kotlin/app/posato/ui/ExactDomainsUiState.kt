package app.posato.ui

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

internal enum class ExactDomainEntryFailure {
    EMPTY,
    TOO_LONG,
    INVALID_DOMAIN,
    DUPLICATE,
    LIMIT_REACHED,
}

internal enum class ExactDomainsOperationFailure {
    LOAD_FAILED,
    REVISION_CONFLICT,
    CORRUPTED_POLICY,
    SAVE_FAILED,
}

@Immutable
internal data class ExactDomainsUiState(
    val domains: PersistentList<String> = persistentListOf(),
    val editorSession: Long = 0,
    val editingDomain: String? = null,
    val inputFailure: ExactDomainEntryFailure? = null,
    val operationFailure: ExactDomainsOperationFailure? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasLoaded: Boolean = false,
) {
    override fun toString(): String {
        return "ExactDomainsUiState(redacted)"
    }
}
