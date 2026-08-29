package app.posato.feature.targets.ui

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

internal enum class ExactDomainEntryFailure { EMPTY, TOO_LONG, INVALID_DOMAIN, DUPLICATE, LIMIT_REACHED }

internal enum class ApplicationPolicyEntryFailure { EMPTY, TOO_LONG, INVALID_CHARACTERS }

internal enum class TargetsOperationFailure { LOAD_FAILED, REVISION_CONFLICT, CORRUPTED_POLICY, SAVE_FAILED }

internal enum class TargetMutation { DOMAIN, APPLICATION_POLICY }

@Immutable
internal data class TargetsUiState(
    val domains: PersistentList<String> = persistentListOf(),
    val domainEditorSession: Long = 0,
    val editingDomain: String? = null,
    val domainInputFailure: ExactDomainEntryFailure? = null,
    val applicationPolicyName: String? = null,
    val applicationEditorSession: Long = 0,
    val isEditingApplicationPolicy: Boolean = false,
    val applicationPolicyInputFailure: ApplicationPolicyEntryFailure? = null,
    val operationFailure: TargetsOperationFailure? = null,
    val savingMutation: TargetMutation? = null,
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
) {
    val isSaving: Boolean
        get() = savingMutation != null

    override fun toString(): String {
        return "TargetsUiState(redacted)"
    }
}
