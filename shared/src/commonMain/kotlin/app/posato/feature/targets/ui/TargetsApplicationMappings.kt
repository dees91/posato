package app.posato.feature.targets.ui

import androidx.lifecycle.viewModelScope
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationRemovalResult
import app.posato.feature.targets.data.LocalApplicationSelectionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun TargetsViewModel.retryApplicationMappings() {
    val state = applicationMappingsState.value
    if (state.isLoading || state.mutation != null) {
        return
    }
    applicationMappingsState.update { current -> current.copy(isLoading = true, failure = null) }
    applicationMappingRefreshRequests.tryEmit(Unit)
}

internal fun TargetsViewModel.chooseApplications() {
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

                is LocalApplicationSelectionResult.AccessChanged -> applicationMappingsState.update { state ->
                    state.copy(snapshot = result.snapshot, access = result.access, failure = null)
                }

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

internal fun TargetsViewModel.removeApplicationMapping(mappingId: LocalApplicationMappingId) {
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

internal fun TargetsViewModel.clearApplicationMappings() {
    if (!currentState.canClearApplicationMappings()) {
        return
    }
    applicationMappingsState.update { state ->
        state.copy(failure = null, mutation = ApplicationMappingMutation.CLEAR)
    }
    viewModelScope.launch {
        try {
            applyApplicationRemovalResult(applicationMappings.clear())
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: Exception) {
            applicationMappingsState.update { state -> state.copy(failure = ApplicationMappingFailure.SAVE_FAILED) }
        } finally {
            applicationMappingsState.update { state -> state.copy(mutation = null) }
        }
    }
}

private fun TargetsViewModel.applyApplicationRemovalResult(result: LocalApplicationRemovalResult) {
    when (result) {
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
}

internal fun TargetsViewModel.observeApplicationMappingReads(): Flow<Unit> {
    return merge(
        applicationMappingRefreshRequests.onStart { emit(Unit) },
        applicationMappings.invalidations,
    ).transform {
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
                        access = result.access,
                    )
                }
            }

            is LocalApplicationMappingsLoadResult.Unavailable -> {
                applicationMappingsState.update {
                    ApplicationMappingsState(
                        snapshot = result.snapshot,
                        isLoading = false,
                        hasLoaded = true,
                        isAvailable = false,
                    )
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
