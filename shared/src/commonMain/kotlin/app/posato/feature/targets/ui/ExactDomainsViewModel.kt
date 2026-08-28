package app.posato.feature.targets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.posato.feature.targets.data.LocalExactDomainPolicyState
import app.posato.feature.targets.data.LocalExactDomainPolicyStore
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicy
import app.posato.feature.targets.domain.ExactDomainPolicyValidationResult
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ExactDomainsViewModel(
    private val store: LocalExactDomainPolicyStore,
) : ViewModel() {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val policyState = MutableStateFlow(ExactDomainsPolicyState(isLoading = true))
    private val editorState = MutableStateFlow(ExactDomainEditorState())
    private val submissionState = MutableStateFlow<ExactDomainsSubmissionState>(ExactDomainsSubmissionState.Idle)
    private val policyReadLifecycle = observePolicyReads()

    val uiState: StateFlow<ExactDomainsUiState> = combine(
        policyState,
        editorState,
        submissionState,
        policyReadLifecycle,
    ) { currentPolicyState, currentEditorState, currentSubmissionState, _ ->
        createUiState(
            policyState = currentPolicyState,
            editorState = currentEditorState,
            submissionState = currentSubmissionState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExactDomainsUiState(),
    )

    fun retry() {
        if (policyState.value.isLoading || submissionState.value is ExactDomainsSubmissionState.Saving) {
            return
        }
        policyState.startLoading()
        submissionState.update { ExactDomainsSubmissionState.Idle }
        refreshRequests.tryEmit(Unit)
    }

    fun beginEditing(canonicalDomain: String) {
        val state = createCurrentUiState()
        if (state.isLoading || state.isSaving || canonicalDomain !in state.domains) {
            return
        }
        editorState.update { state ->
            ExactDomainEditorState(
                session = state.session + 1,
                editingDomain = canonicalDomain,
            )
        }
        submissionState.update { ExactDomainsSubmissionState.Idle }
    }

    fun cancelEditing() {
        if (submissionState.value is ExactDomainsSubmissionState.Saving) {
            return
        }
        editorState.reset()
    }

    fun submit(input: String) {
        val state = createCurrentUiState()
        if (!state.canMutatePolicy()) {
            return
        }
        when (val submission = createSubmission(state, input)) {
            is ExactDomainSubmission.Ready -> persist(submission.canonicalDomains)
            is ExactDomainSubmission.EntryFailed -> editorState.showFailure(submission.failure)
            is ExactDomainSubmission.OperationFailed -> submissionState.showFailure(submission.failure)
        }
    }

    fun remove(canonicalDomain: String) {
        val state = createCurrentUiState()
        val canRemoveDomain = state.canMutatePolicy() && canonicalDomain in state.domains
        if (canRemoveDomain) {
            persist(state.domains - canonicalDomain)
        }
    }

    private fun observePolicyReads(): Flow<Unit> {
        return refreshRequests
            .onStart { emit(Unit) }
            .transform {
                policyState.startLoading()
                emit(Unit)
                applyReadResult(store.read())
            }
    }

    private fun applyReadResult(result: LocalPolicyResult<LocalExactDomainPolicyState>) {
        when (result) {
            is LocalPolicyResult.Success -> {
                editorState.reconcileWith(result.value)
                policyState.update { ExactDomainsPolicyState(snapshot = result.value) }
            }

            is LocalPolicyResult.Failure -> {
                policyState.update { state ->
                    ExactDomainsPolicyState(
                        snapshot = state.snapshot,
                        failure = result.reason.toLoadFailure(),
                    )
                }
            }
        }
    }

    private fun persist(canonicalDomains: List<String>) {
        val policy = when (val result = ExactDomainPolicy.fromCanonicalValues(canonicalDomains)) {
            is ExactDomainPolicyValidationResult.Success -> {
                result.policy
            }

            is ExactDomainPolicyValidationResult.Failure -> {
                editorState.showFailure(
                    failure = ExactDomainEntryFailure.LIMIT_REACHED,
                )
                return
            }
        }
        val expectedRevision = policyState.value.snapshot?.revision
        if (expectedRevision == null) {
            submissionState.showFailure(ExactDomainsOperationFailure.LOAD_FAILED)
            return
        }
        submissionState.update { ExactDomainsSubmissionState.Saving }
        viewModelScope.launch {
            try {
                handleReplaceResult(store.replace(expectedRevision, policy))
            } finally {
                submissionState.update { state ->
                    if (state is ExactDomainsSubmissionState.Saving) {
                        ExactDomainsSubmissionState.Idle
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun handleReplaceResult(result: LocalPolicyResult<LocalExactDomainPolicyState>) {
        when (result) {
            is LocalPolicyResult.Success -> {
                policyState.update { ExactDomainsPolicyState(snapshot = result.value) }
                editorState.reset()
                submissionState.update { ExactDomainsSubmissionState.Idle }
            }

            is LocalPolicyResult.Failure -> {
                submissionState.showFailure(result.reason.toSaveFailure())
            }
        }
    }

    private fun createCurrentUiState(): ExactDomainsUiState {
        return createUiState(
            policyState.value,
            editorState.value,
            submissionState.value,
        )
    }
}

private data class ExactDomainsPolicyState(
    val snapshot: LocalExactDomainPolicyState? = null,
    val isLoading: Boolean = false,
    val failure: ExactDomainsOperationFailure? = null,
) {
    override fun toString(): String {
        return "ExactDomainsPolicyState(redacted)"
    }
}

private data class ExactDomainEditorState(
    val session: Long = 0,
    val editingDomain: String? = null,
    val failure: ExactDomainEntryFailure? = null,
) {
    override fun toString(): String {
        return "ExactDomainEditorState(redacted)"
    }
}

private sealed interface ExactDomainsSubmissionState {
    data object Idle : ExactDomainsSubmissionState

    data object Saving : ExactDomainsSubmissionState

    data class Failed(
        val failure: ExactDomainsOperationFailure,
    ) : ExactDomainsSubmissionState
}

private fun ExactDomainsUiState.canMutatePolicy(): Boolean {
    return hasLoaded && !isLoading && !isSaving
}

private fun MutableStateFlow<ExactDomainEditorState>.showFailure(failure: ExactDomainEntryFailure) {
    update { state -> state.copy(failure = failure) }
}

private fun MutableStateFlow<ExactDomainsPolicyState>.startLoading() {
    update { state ->
        state.copy(
            isLoading = true,
            failure = null,
        )
    }
}

private fun MutableStateFlow<ExactDomainEditorState>.reset() {
    update { state -> ExactDomainEditorState(session = state.session + 1) }
}

private fun MutableStateFlow<ExactDomainEditorState>.reconcileWith(snapshot: LocalExactDomainPolicyState) {
    update { state ->
        val editingDomain = state.editingDomain
        val editingDomainExists = editingDomain == null || snapshot.policy.domains.any { domain ->
            domain.canonicalValue == editingDomain
        }

        if (editingDomainExists) {
            state
        } else {
            ExactDomainEditorState(session = state.session + 1)
        }
    }
}

private fun MutableStateFlow<ExactDomainsSubmissionState>.showFailure(failure: ExactDomainsOperationFailure) {
    update { ExactDomainsSubmissionState.Failed(failure) }
}

private fun createUiState(
    policyState: ExactDomainsPolicyState,
    editorState: ExactDomainEditorState,
    submissionState: ExactDomainsSubmissionState,
): ExactDomainsUiState {
    val submissionFailure = (submissionState as? ExactDomainsSubmissionState.Failed)?.failure

    return ExactDomainsUiState(
        domains = policyState.snapshot?.policy?.canonicalValues().orEmpty().toPersistentList(),
        editorSession = editorState.session,
        editingDomain = editorState.editingDomain,
        inputFailure = editorState.failure,
        operationFailure = submissionFailure ?: policyState.failure,
        isLoading = policyState.isLoading,
        isSaving = submissionState is ExactDomainsSubmissionState.Saving,
        hasLoaded = policyState.snapshot != null,
    )
}

private fun LocalPolicyFailure.toLoadFailure(): ExactDomainsOperationFailure {
    return when (this) {
        LocalPolicyFailure.CORRUPTION -> ExactDomainsOperationFailure.CORRUPTED_POLICY
        else -> ExactDomainsOperationFailure.LOAD_FAILED
    }
}

private fun LocalPolicyFailure.toSaveFailure(): ExactDomainsOperationFailure {
    return when (this) {
        LocalPolicyFailure.REVISION_CONFLICT -> ExactDomainsOperationFailure.REVISION_CONFLICT
        LocalPolicyFailure.CORRUPTION -> ExactDomainsOperationFailure.CORRUPTED_POLICY
        else -> ExactDomainsOperationFailure.SAVE_FAILED
    }
}

private fun ExactDomainPolicy.canonicalValues(): List<String> {
    return domains.map(ExactDomain::canonicalValue)
}
