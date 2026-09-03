package app.posato.feature.targets.ui

import androidx.compose.runtime.Immutable
import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

internal enum class ExactDomainEntryFailure { EMPTY, TOO_LONG, INVALID_DOMAIN, DUPLICATE, LIMIT_REACHED }

internal enum class ApplicationPolicyEntryFailure { EMPTY, TOO_LONG, INVALID_CHARACTERS }

internal enum class TargetsOperationFailure { LOAD_FAILED, REVISION_CONFLICT, CORRUPTED_POLICY, SAVE_FAILED }

internal enum class TargetMutation { DOMAIN, APPLICATION_POLICY }

internal enum class ApplicationMappingFailure {
    LOAD_FAILED,
    CORRUPTED_MAPPINGS,
    CORRUPTED_CLEAR_FAILED,
    PICKER_FAILED,
    SAVE_FAILED,
    SELF_SELECTION,
    INVALID_OR_UNSIGNED,
    UNSUPPORTED_SELECTION,
    CAPACITY,
}

internal enum class ApplicationMappingMutation { CHOOSE, REMOVE, CLEAR }

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
    val applicationMappings: PersistentList<LocalApplicationMapping> = persistentListOf(),
    val applicationMappingFailure: ApplicationMappingFailure? = null,
    val applicationMappingMutation: ApplicationMappingMutation? = null,
    val isApplicationMappingLoading: Boolean = true,
    val hasLoadedApplicationMappings: Boolean = false,
    val isApplicationMappingAvailable: Boolean = false,
    val applicationMappingsAccess: LocalApplicationMappingsAccess? = null,
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
) {
    val isSaving: Boolean
        get() = savingMutation != null

    val isMutatingApplicationMappings: Boolean
        get() = applicationMappingMutation != null

    val hasApplicationMappingLoadFailure: Boolean
        get() = applicationMappingFailure == ApplicationMappingFailure.LOAD_FAILED ||
            applicationMappingFailure == ApplicationMappingFailure.CORRUPTED_MAPPINGS ||
            applicationMappingFailure == ApplicationMappingFailure.CORRUPTED_CLEAR_FAILED

    override fun toString(): String {
        return "TargetsUiState(redacted)"
    }
}

internal fun TargetsUiState.canChooseApplications(): Boolean {
    val canRequestSelection = applicationMappingsAccess == LocalApplicationMappingsAccess.READY ||
        applicationMappingsAccess == LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED

    return applicationPolicyName != null && canRequestSelection && canMutateApplicationMappings()
}

internal fun TargetsUiState.canRemoveApplicationMapping(mappingId: LocalApplicationMappingId): Boolean {
    return canMutateApplicationMappings() && applicationMappings.any { mapping -> mapping.id == mappingId }
}

internal fun TargetsUiState.canClearApplicationMappings(): Boolean {
    if (!hasLoadedApplicationMappings || isApplicationMappingLoading || isMutatingApplicationMappings) {
        return false
    }
    if (applicationMappingFailure == ApplicationMappingFailure.CORRUPTED_MAPPINGS ||
        applicationMappingFailure == ApplicationMappingFailure.CORRUPTED_CLEAR_FAILED
    ) {
        return true
    }
    return canMutateApplicationMappings() && applicationMappings.isNotEmpty()
}

private fun TargetsUiState.canMutateApplicationMappings(): Boolean {
    return hasLoadedApplicationMappings && isApplicationMappingAvailable &&
        !isApplicationMappingLoading && !isMutatingApplicationMappings && !hasApplicationMappingLoadFailure
}
