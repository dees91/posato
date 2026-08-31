package app.posato.feature.targets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationRemovalResult
import app.posato.feature.targets.data.LocalApplicationSelectionResult
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.UnavailableLocalApplicationMappings
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ApplicationPolicyNameResult
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationFailure
import app.posato.feature.targets.domain.TargetPolicyValidationResult
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

@Suppress("TooManyFunctions")
internal class TargetsViewModel(
    private val store: LocalTargetPolicyStore,
    private val applicationMappings: LocalApplicationMappings = UnavailableLocalApplicationMappings,
) : ViewModel() {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val applicationMappingRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val policyState = MutableStateFlow(TargetsPolicyState(isLoading = true))
    private val applicationMappingsState = MutableStateFlow(ApplicationMappingsState())
    private val domainEditorState = MutableStateFlow(ExactDomainEditorState())
    private val applicationEditorState = MutableStateFlow(ApplicationPolicyEditorState())
    private val submissionState = MutableStateFlow<TargetsSubmissionState>(TargetsSubmissionState.Idle)
    private val policyReadLifecycle = observePolicyReads()
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
    private val currentState: TargetsUiState
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
    ) { presentation, currentApplicationMappingsState, _, _ ->
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

    fun retryApplicationMappings() {
        val state = applicationMappingsState.value
        if (state.isLoading || state.mutation != null) {
            return
        }
        applicationMappingsState.update { current -> current.copy(isLoading = true, failure = null) }
        applicationMappingRefreshRequests.tryEmit(Unit)
    }

    fun chooseApplications() {
        if (!currentState.canChooseApplications()) {
            return
        }
        applicationMappingsState.update { state ->
            state.copy(failure = null, mutation = ApplicationMappingMutation.CHOOSE)
        }
        viewModelScope.launch {
            try {
                when (val result = applicationMappings.chooseApplications()) {
                    is LocalApplicationSelectionResult.Success -> applicationMappingsState.update { state ->
                        state.copy(snapshot = result.snapshot, failure = null)
                    }

                    LocalApplicationSelectionResult.Cancelled -> Unit

                    LocalApplicationSelectionResult.Unavailable -> applicationMappingsState.update { state ->
                        state.copy(isAvailable = false)
                    }

                    is LocalApplicationSelectionResult.Rejected -> applicationMappingsState.update { state ->
                        state.copy(failure = result.reason.toUiFailure())
                    }

                    is LocalApplicationSelectionResult.Failure -> applicationMappingsState.update { state ->
                        state.copy(failure = result.reason.toUiFailure())
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (_: Exception) {
                applicationMappingsState.update { state -> state.copy(failure = ApplicationMappingFailure.PICKER_FAILED) }
            } finally {
                applicationMappingsState.update { state -> state.copy(mutation = null) }
            }
        }
    }

    fun removeApplicationMapping(mappingId: LocalApplicationMappingId) {
        if (!currentState.canRemoveApplicationMapping(mappingId)) {
            return
        }
        applicationMappingsState.update { state ->
            state.copy(failure = null, mutation = ApplicationMappingMutation.REMOVE)
        }
        viewModelScope.launch {
            try {
                when (val result = applicationMappings.remove(mappingId)) {
                    is LocalApplicationRemovalResult.Success -> applicationMappingsState.update { state ->
                        state.copy(snapshot = result.snapshot, failure = null)
                    }

                    LocalApplicationRemovalResult.Unavailable -> applicationMappingsState.update { state ->
                        state.copy(isAvailable = false)
                    }

                    is LocalApplicationRemovalResult.Failure -> applicationMappingsState.update { state ->
                        state.copy(failure = result.reason.toUiFailure())
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (_: Exception) {
                applicationMappingsState.update { state -> state.copy(failure = ApplicationMappingFailure.SAVE_FAILED) }
            } finally {
                applicationMappingsState.update { state -> state.copy(mutation = null) }
            }
        }
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

    private fun observePolicyReads(): Flow<Unit> {
        return refreshRequests.onStart { emit(Unit) }.transform {
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
    }

    private fun observeApplicationMappingReads(): Flow<Unit> {
        return applicationMappingRefreshRequests.onStart { emit(Unit) }.transform {
            applicationMappingsState.update { state -> state.copy(isLoading = true, failure = null) }
            emit(Unit)
            when (val result = applicationMappings.load()) {
                is LocalApplicationMappingsLoadResult.Success -> {
                    applicationMappingsState.update {
                        ApplicationMappingsState(
                            snapshot = result.snapshot,
                            isLoading = false,
                            hasLoaded = true,
                            isAvailable = true,
                        )
                    }
                }

                LocalApplicationMappingsLoadResult.Unavailable -> {
                    applicationMappingsState.update {
                        ApplicationMappingsState(isLoading = false, hasLoaded = true, isAvailable = false)
                    }
                }

                is LocalApplicationMappingsLoadResult.Failure -> {
                    applicationMappingsState.update { state ->
                        state.copy(
                            isLoading = false,
                            hasLoaded = true,
                            isAvailable = true,
                            failure = result.reason.toUiFailure(),
                        )
                    }
                }
            }
        }
    }

    private fun persist(
        canonicalDomains: List<String>,
        applicationPolicyName: String?,
        mutation: TargetMutation
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
                return
            }
        }
        val expectedRevision = policyState.value.snapshot?.revision
        if (expectedRevision == null) {
            submissionState.update { TargetsSubmissionState.Failed(TargetsOperationFailure.LOAD_FAILED) }
            return
        }
        submissionState.update { TargetsSubmissionState.Saving(mutation) }
        viewModelScope.launch {
            try {
                when (val result = store.replace(expectedRevision, policy)) {
                    is LocalPolicyResult.Success -> {
                        policyState.update { TargetsPolicyState(snapshot = result.value) }
                        when (mutation) {
                            TargetMutation.DOMAIN -> domainEditorState.resetDomainEditor()
                            TargetMutation.APPLICATION_POLICY -> applicationEditorState.resetApplicationEditor()
                        }
                        submissionState.update { TargetsSubmissionState.Idle }
                    }

                    is LocalPolicyResult.Failure -> {
                        submissionState.update { TargetsSubmissionState.Failed(result.reason.toSaveFailure()) }
                    }
                }
            } finally {
                submissionState.update { state -> if (state is TargetsSubmissionState.Saving) TargetsSubmissionState.Idle else state }
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
