package app.posato.feature.targets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.UnavailableLocalApplicationMappings
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ApplicationPolicyNameResult
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationFailure
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
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

internal class TargetsViewModel(
    private val store: LocalTargetPolicyStore,
    internal val applicationMappings: LocalApplicationMappings = UnavailableLocalApplicationMappings,
) : ViewModel() {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val applicationMappingRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val policyState = MutableStateFlow(TargetsPolicyState(isLoading = true))
    internal val applicationMappingsState = MutableStateFlow(ApplicationMappingsState())
    private val domainEditorState = MutableStateFlow(ExactDomainEditorState())
    private val applicationEditorState = MutableStateFlow(ApplicationPolicyEditorState())
    private val submissionState = MutableStateFlow<TargetsSubmissionState>(TargetsSubmissionState.Idle)
    private val policyReadLifecycle: Flow<Unit> = refreshRequests.onStart { emit(Unit) }.transform {
        policyState.update { state -> state.copy(isLoading = true, failure = null) }
        emit(Unit)
        when (val result = store.read()) {
            is LocalPolicyResult.Success -> {
                domainEditorState.reconcileDomainEditorWith(result.value)
                applicationEditorState.reconcileApplicationEditorWith(result.value)
                policyState.update { TargetsPolicyState(snapshot = result.value) }
            }

            is LocalPolicyResult.Failure -> {
                policyState.update { state ->
                    TargetsPolicyState(snapshot = state.snapshot, failure = result.reason.toLoadFailure())
                }
            }
        }
    }
    private val policySignalLifecycle: Flow<Unit> = store.policyChanges.transform {
        when (val result = store.read()) {
            is LocalPolicyResult.Success -> {
                domainEditorState.reconcileDomainEditorWith(result.value)
                applicationEditorState.reconcileApplicationEditorWith(result.value)
                policyState.update { TargetsPolicyState(snapshot = result.value) }
            }

            is LocalPolicyResult.Failure -> {
                policyState.update { state ->
                    TargetsPolicyState(snapshot = state.snapshot, failure = result.reason.toLoadFailure())
                }
            }
        }
        emit(Unit)
    }.onStart { emit(Unit) }
    private val applicationMappingsReadLifecycle = observeApplicationMappingReads()
    private val policyPresentationState = combine(
        policyState,
        domainEditorState,
        applicationEditorState,
        submissionState,
    ) { currentPolicyState, currentDomainEditorState, currentApplicationEditorState, currentSubmissionState ->
        TargetsPolicyPresentationState(
            currentPolicyState,
            currentDomainEditorState,
            currentApplicationEditorState,
            currentSubmissionState,
        )
    }
    internal val currentState: TargetsUiState
        get() = createUiState(
            policyState.value,
            domainEditorState.value,
            applicationEditorState.value,
            submissionState.value,
            applicationMappingsState.value,
        )

    val uiState: StateFlow<TargetsUiState> = combine(
        policyPresentationState,
        applicationMappingsState,
        policyReadLifecycle,
        applicationMappingsReadLifecycle,
        policySignalLifecycle,
    ) { presentation, currentApplicationMappingsState, _, _, _ ->
        createUiState(
            presentation.policyState,
            presentation.domainEditorState,
            presentation.applicationEditorState,
            presentation.submissionState,
            currentApplicationMappingsState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TargetsUiState())

    fun retry() {
        if (policyState.value.isLoading || submissionState.value is TargetsSubmissionState.Saving) {
            return
        }
        policyState.update { state -> state.copy(isLoading = true, failure = null) }
        submissionState.update { TargetsSubmissionState.Idle }
        refreshRequests.tryEmit(Unit)
    }

    fun beginEditingDomain(canonicalDomain: String) {
        val state = currentState
        if (!state.canMutatePolicy() || canonicalDomain !in state.domains) {
            return
        }
        domainEditorState.update { editor -> ExactDomainEditorState(editor.session + 1, canonicalDomain) }
        submissionState.update { TargetsSubmissionState.Idle }
    }

    fun cancelEditingDomain() {
        if (submissionState.value !is TargetsSubmissionState.Saving) {
            domainEditorState.resetDomainEditor()
        }
    }

    fun submitDomain(input: String) {
        val state = currentState
        if (!state.canMutatePolicy()) {
            return
        }
        when (val submission = createExactDomainSubmission(state, input)) {
            is ExactDomainSubmission.Ready -> persist(submission.canonicalDomains, state.applicationPolicyName, TargetMutation.DOMAIN)
            is ExactDomainSubmission.EntryFailed -> domainEditorState.update { editor -> editor.copy(failure = submission.failure) }
            is ExactDomainSubmission.OperationFailed -> submissionState.update { TargetsSubmissionState.Failed(submission.failure) }
        }
    }

    fun removeDomain(canonicalDomain: String) {
        val state = currentState
        if (state.canMutatePolicy() && canonicalDomain in state.domains) {
            persist(state.domains - canonicalDomain, state.applicationPolicyName, TargetMutation.DOMAIN)
        }
    }

    fun submitWebsites(
        input: String,
        submissionId: Long
    ) {
        val state = currentState
        if (!state.canMutatePolicy() || state.editingDomain != null) {
            domainEditorState.update { it.copy(batchReceipt = WebsiteBatchReceipt(submissionId, saved = false)) }
            return
        }
        when (val submission = createWebsiteBatchSubmission(input, state.domains)) {
            WebsiteBatchSubmission.TooLong -> {
                domainEditorState.update { it.copy(batchReceipt = WebsiteBatchReceipt(submissionId, saved = false, tooLong = true)) }
            }

            is WebsiteBatchSubmission.Ready -> {
                val receipt = WebsiteBatchReceipt(
                    submissionId = submissionId,
                    saved = true,
                    addedCount = submission.addedCount,
                    duplicateCount = submission.duplicateCount,
                    rejectedIndices = submission.rejectedIndices.toPersistentList(),
                )
                if (submission.addedCount == 0) {
                    domainEditorState.update { it.copy(batchReceipt = receipt) }
                } else {
                    persist(submission.canonicalDomains, state.applicationPolicyName, TargetMutation.DOMAIN) { saved ->
                        domainEditorState.update { it.copy(batchReceipt = if (saved) receipt else WebsiteBatchReceipt(submissionId, saved = false)) }
                    }
                }
            }
        }
    }

    fun beginEditingApplicationPolicy() {
        val state = currentState
        val currentName = state.applicationPolicyName
        if (!state.canMutatePolicy() || currentName == null) {
            return
        }
        applicationEditorState.update { editor ->
            ApplicationPolicyEditorState(editor.session + 1, isEditing = true, originalName = currentName)
        }
        submissionState.update { TargetsSubmissionState.Idle }
    }

    fun cancelEditingApplicationPolicy() {
        if (submissionState.value !is TargetsSubmissionState.Saving) {
            applicationEditorState.resetApplicationEditor()
        }
    }

    fun submitApplicationPolicy(input: String) {
        val state = currentState
        if (!state.canMutatePolicy()) {
            return
        }
        when (val result = ApplicationPolicyName.parse(input)) {
            is ApplicationPolicyNameResult.Success -> {
                applicationEditorState.update { editor -> editor.copy(failure = null) }
                persist(state.domains, result.name.canonicalValue, TargetMutation.APPLICATION_POLICY)
            }

            is ApplicationPolicyNameResult.Failure -> {
                applicationEditorState.update { editor -> editor.copy(failure = result.reason.toEntryFailure()) }
            }
        }
    }

    fun removeApplicationPolicy() {
        val state = currentState
        if (state.canMutatePolicy() && state.applicationPolicyName != null) {
            persist(state.domains, null, TargetMutation.APPLICATION_POLICY)
        }
    }

    private fun persist(
        canonicalDomains: List<String>,
        applicationPolicyName: String?,
        mutation: TargetMutation,
        onCompleted: (Boolean) -> Unit = {},
    ) {
        val policy = when (val result = TargetPolicy.fromStoredValues(canonicalDomains, applicationPolicyName)) {
            is TargetPolicyValidationResult.Success -> {
                result.policy
            }

            is TargetPolicyValidationResult.Failure -> {
                if (result.reason == TargetPolicyValidationFailure.TOO_MANY_DOMAINS) {
                    domainEditorState.update { editor -> editor.copy(failure = ExactDomainEntryFailure.LIMIT_REACHED) }
                } else {
                    submissionState.update { TargetsSubmissionState.Failed(TargetsOperationFailure.CORRUPTED_POLICY) }
                }
                onCompleted(false)
                return
            }
        }
        val expectedRevision = policyState.value.snapshot?.revision
        if (expectedRevision == null) {
            submissionState.update { TargetsSubmissionState.Failed(TargetsOperationFailure.LOAD_FAILED) }
            onCompleted(false)
            return
        }
        submissionState.update { TargetsSubmissionState.Saving(mutation) }
        viewModelScope.launch {
            var saved = false
            try {
                when (val result = store.replace(expectedRevision, policy)) {
                    is LocalPolicyResult.Success -> {
                        policyState.update { TargetsPolicyState(snapshot = result.value) }
                        when (mutation) {
                            TargetMutation.DOMAIN -> domainEditorState.resetDomainEditor()
                            TargetMutation.APPLICATION_POLICY -> applicationEditorState.resetApplicationEditor()
                        }
                        submissionState.update { TargetsSubmissionState.Idle }
                        saved = true
                    }

                    is LocalPolicyResult.Failure -> {
                        submissionState.update { TargetsSubmissionState.Failed(result.reason.toSaveFailure()) }
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (_: Exception) {
                submissionState.update { TargetsSubmissionState.Failed(TargetsOperationFailure.SAVE_FAILED) }
            } finally {
                submissionState.update { state -> if (state is TargetsSubmissionState.Saving) TargetsSubmissionState.Idle else state }
                onCompleted(saved)
            }
        }
    }
}

private data class TargetsPolicyPresentationState(
    val policyState: TargetsPolicyState,
    val domainEditorState: ExactDomainEditorState,
    val applicationEditorState: ApplicationPolicyEditorState,
    val submissionState: TargetsSubmissionState,
) {
    override fun toString(): String {
        return "TargetsPolicyPresentationState(redacted)"
    }
}
