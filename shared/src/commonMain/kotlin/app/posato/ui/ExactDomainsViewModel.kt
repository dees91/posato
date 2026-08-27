package app.posato.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.posato.persistence.LocalExactDomainPolicyState
import app.posato.persistence.LocalExactDomainPolicyStore
import app.posato.persistence.LocalPolicyFailure
import app.posato.persistence.LocalPolicyResult
import app.posato.policy.ExactDomain
import app.posato.policy.ExactDomainInputFailure
import app.posato.policy.ExactDomainInputResult
import app.posato.policy.ExactDomainPolicy
import app.posato.policy.ExactDomainPolicyValidationResult
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

internal class ExactDomainsViewModel(
    private val store: LocalExactDomainPolicyStore,
) : ViewModel() {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val savedSnapshots = MutableSharedFlow<LocalExactDomainPolicyState>(extraBufferCapacity = 1)
    private val latestPolicySnapshot = MutableStateFlow<LocalExactDomainPolicyState?>(null)
    private val editorState = MutableStateFlow(ExactDomainEditorState())
    private val submissionState = MutableStateFlow<ExactDomainsSubmissionState>(ExactDomainsSubmissionState.Idle)
    private val policyState = observePolicyState()

    val uiState: StateFlow<ExactDomainsUiState> =
        combine(
            policyState,
            editorState,
            submissionState,
            ::createUiState,
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExactDomainsUiState(),
        )

    fun retry() {
        if (submissionState.value is ExactDomainsSubmissionState.Saving) {
            return
        }
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

    private fun observePolicyState(): Flow<ExactDomainsPolicyState> {
        val readResults = refreshRequests
            .onStart { emit(Unit) }
            .map { store.read() }
        val savedResults = savedSnapshots.map { snapshot -> LocalPolicyResult.Success(snapshot) }

        return merge(readResults, savedResults)
            .map(::toPolicyState)
            .onStart {
                val snapshot = latestPolicySnapshot.value
                emit(
                    ExactDomainsPolicyState(
                        snapshot = snapshot,
                        isLoading = snapshot == null,
                    ),
                )
            }
    }

    private fun toPolicyState(result: LocalPolicyResult<LocalExactDomainPolicyState>): ExactDomainsPolicyState {
        return when (result) {
            is LocalPolicyResult.Success -> {
                latestPolicySnapshot.update { result.value }
                ExactDomainsPolicyState(snapshot = result.value)
            }

            is LocalPolicyResult.Failure -> {
                ExactDomainsPolicyState(
                    snapshot = latestPolicySnapshot.value,
                    failure = result.reason.toLoadFailure(),
                )
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
        val expectedRevision = latestPolicySnapshot.value?.revision
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

    private suspend fun handleReplaceResult(result: LocalPolicyResult<LocalExactDomainPolicyState>) {
        when (result) {
            is LocalPolicyResult.Success -> {
                latestPolicySnapshot.update { result.value }
                editorState.reset()
                savedSnapshots.emit(result.value)
                submissionState.update { ExactDomainsSubmissionState.Idle }
            }

            is LocalPolicyResult.Failure -> {
                submissionState.showFailure(result.reason.toSaveFailure())
            }
        }
    }

    private fun createCurrentUiState(): ExactDomainsUiState {
        val snapshot = latestPolicySnapshot.value

        return createUiState(
            ExactDomainsPolicyState(
                snapshot = snapshot,
                isLoading = snapshot == null,
            ),
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

private sealed interface ExactDomainSubmission {
    class Ready(
        val canonicalDomains: List<String>,
    ) : ExactDomainSubmission {
        override fun toString(): String {
            return "ExactDomainSubmission.Ready(redacted)"
        }
    }

    data class EntryFailed(
        val failure: ExactDomainEntryFailure,
    ) : ExactDomainSubmission

    data class OperationFailed(
        val failure: ExactDomainsOperationFailure,
    ) : ExactDomainSubmission
}

private fun createSubmission(
    state: ExactDomainsUiState,
    input: String,
): ExactDomainSubmission {
    val domainResult = ExactDomain.parse(input)
    if (domainResult is ExactDomainInputResult.Failure) {
        return ExactDomainSubmission.EntryFailed(domainResult.reason.toEntryFailure())
    }
    val canonicalValue = (domainResult as ExactDomainInputResult.Success).domain.canonicalValue
    val isDuplicate = state.domains.any { existingDomain ->
        existingDomain == canonicalValue && existingDomain != state.editingDomain
    }

    return when {
        isDuplicate -> {
            ExactDomainSubmission.EntryFailed(ExactDomainEntryFailure.DUPLICATE)
        }

        state.editingDomain == null -> {
            ExactDomainSubmission.Ready(state.domains + canonicalValue)
        }

        state.editingDomain !in state.domains -> {
            ExactDomainSubmission.OperationFailed(ExactDomainsOperationFailure.REVISION_CONFLICT)
        }

        else -> {
            ExactDomainSubmission.Ready(
                state.domains.map { existingDomain ->
                    if (existingDomain == state.editingDomain) canonicalValue else existingDomain
                },
            )
        }
    }
}

private fun ExactDomainsUiState.canMutatePolicy(): Boolean {
    return hasLoaded && !isLoading && !isSaving
}

private fun MutableStateFlow<ExactDomainEditorState>.showFailure(failure: ExactDomainEntryFailure) {
    update { state -> state.copy(failure = failure) }
}

private fun MutableStateFlow<ExactDomainEditorState>.reset() {
    update { state -> ExactDomainEditorState(session = state.session + 1) }
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

private fun ExactDomainInputFailure.toEntryFailure(): ExactDomainEntryFailure {
    return when (this) {
        ExactDomainInputFailure.EMPTY -> ExactDomainEntryFailure.EMPTY
        ExactDomainInputFailure.TOO_LONG -> ExactDomainEntryFailure.TOO_LONG
        ExactDomainInputFailure.INVALID_DOMAIN -> ExactDomainEntryFailure.INVALID_DOMAIN
    }
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
