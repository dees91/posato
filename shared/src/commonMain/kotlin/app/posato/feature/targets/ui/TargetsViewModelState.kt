package app.posato.feature.targets.ui

import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ApplicationPolicyNameFailure
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class TargetsPolicyState(
    val snapshot: LocalTargetPolicyState? = null,
    val isLoading: Boolean = false,
    val failure: TargetsOperationFailure? = null,
) {
    override fun toString(): String {
        return "TargetsPolicyState(redacted)"
    }
}

internal data class ExactDomainEditorState(
    val session: Long = 0,
    val editingDomain: String? = null,
    val failure: ExactDomainEntryFailure? = null,
) {
    override fun toString(): String {
        return "ExactDomainEditorState(redacted)"
    }
}

internal data class ApplicationPolicyEditorState(
    val session: Long = 0,
    val isEditing: Boolean = false,
    val originalName: String? = null,
    val failure: ApplicationPolicyEntryFailure? = null,
) {
    override fun toString(): String {
        return "ApplicationPolicyEditorState(redacted)"
    }
}

internal sealed interface TargetsSubmissionState {
    data object Idle : TargetsSubmissionState

    data class Saving(
        val mutation: TargetMutation
    ) : TargetsSubmissionState

    data class Failed(
        val failure: TargetsOperationFailure
    ) : TargetsSubmissionState
}

internal fun MutableStateFlow<ExactDomainEditorState>.resetDomainEditor() {
    update { state -> ExactDomainEditorState(session = state.session + 1) }
}

internal fun MutableStateFlow<ApplicationPolicyEditorState>.resetApplicationEditor() {
    update { state -> ApplicationPolicyEditorState(session = state.session + 1) }
}

internal fun MutableStateFlow<ExactDomainEditorState>.reconcileDomainEditorWith(snapshot: LocalTargetPolicyState) {
    update { state ->
        val editingDomain = state.editingDomain
        val remainsPresent = editingDomain == null || snapshot.policy.domains.any { domain -> domain.canonicalValue == editingDomain }
        if (remainsPresent) state else ExactDomainEditorState(session = state.session + 1)
    }
}

internal fun MutableStateFlow<ApplicationPolicyEditorState>.reconcileApplicationEditorWith(snapshot: LocalTargetPolicyState) {
    update { state ->
        val storedName = snapshot.policy.applicationPolicyName?.canonicalValue
        if (!state.isEditing || storedName == state.originalName) state else ApplicationPolicyEditorState(session = state.session + 1)
    }
}

internal fun createUiState(
    policyState: TargetsPolicyState,
    domainEditorState: ExactDomainEditorState,
    applicationEditorState: ApplicationPolicyEditorState,
    submissionState: TargetsSubmissionState,
): TargetsUiState {
    val policy = policyState.snapshot?.policy

    return TargetsUiState(
        domains = policy?.domains.orEmpty().map { domain -> domain.canonicalValue }.toPersistentList(),
        domainEditorSession = domainEditorState.session,
        editingDomain = domainEditorState.editingDomain,
        domainInputFailure = domainEditorState.failure,
        applicationPolicyName = policy?.applicationPolicyName?.canonicalValue,
        applicationEditorSession = applicationEditorState.session,
        isEditingApplicationPolicy = applicationEditorState.isEditing,
        applicationPolicyInputFailure = applicationEditorState.failure,
        operationFailure = (submissionState as? TargetsSubmissionState.Failed)?.failure ?: policyState.failure,
        savingMutation = (submissionState as? TargetsSubmissionState.Saving)?.mutation,
        isLoading = policyState.isLoading,
        hasLoaded = policyState.snapshot != null,
    )
}

internal fun ApplicationPolicyNameFailure.toEntryFailure(): ApplicationPolicyEntryFailure {
    return when (this) {
        ApplicationPolicyNameFailure.EMPTY -> ApplicationPolicyEntryFailure.EMPTY
        ApplicationPolicyNameFailure.TOO_LONG -> ApplicationPolicyEntryFailure.TOO_LONG
        ApplicationPolicyNameFailure.INVALID_CHARACTERS -> ApplicationPolicyEntryFailure.INVALID_CHARACTERS
    }
}

internal fun LocalPolicyFailure.toLoadFailure(): TargetsOperationFailure {
    return if (this == LocalPolicyFailure.CORRUPTION) TargetsOperationFailure.CORRUPTED_POLICY else TargetsOperationFailure.LOAD_FAILED
}

internal fun LocalPolicyFailure.toSaveFailure(): TargetsOperationFailure {
    return when (this) {
        LocalPolicyFailure.REVISION_CONFLICT -> TargetsOperationFailure.REVISION_CONFLICT
        LocalPolicyFailure.CORRUPTION -> TargetsOperationFailure.CORRUPTED_POLICY
        else -> TargetsOperationFailure.SAVE_FAILED
    }
}

internal fun TargetsUiState.canMutatePolicy(): Boolean {
    return hasLoaded && !isLoading && !isSaving
}
