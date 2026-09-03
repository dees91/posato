package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.SyncContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface WorkspaceKeyRead {
    data class Ready(
        val context: SyncContext,
        val key: WorkspaceKeyValue
    ) : WorkspaceKeyRead {
        override fun toString(): String {
            return "WorkspaceKeyRead.Ready(redacted)"
        }
    }

    data object WaitingForWorkspaceKey : WorkspaceKeyRead

    data object Retryable : WorkspaceKeyRead

    data object ActionRequired : WorkspaceKeyRead
}

internal class BootstrapCoordinator(
    private val account: BootstrapAccountPort,
    private val cloud: BootstrapCloudPort,
    keys: BootstrapKeyPort,
    private val store: BootstrapStore,
    crypto: SyncCryptoProvider
) {
    private val mutex = Mutex()
    private val zones = BootstrapZonePhase(cloud)
    private val items = BootstrapItemCheck(keys)
    private val anchors = BootstrapAnchorPhase(cloud, keys, items, store)
    private val candidates = BootstrapCandidatePhase(keys, crypto, items)
    private val keyReads = BootstrapKeyReadPhase(items, store)

    suspend fun bootstrap(): BootstrapResult = mutex.withLock {
        when (val gate = resolveUsableBinding()) {
            is BindingGate.Stop -> gate.result
            is BindingGate.Use -> bootstrapWithBinding(gate.binding)
        }
    }

    suspend fun establishedContext(): SyncContext? = mutex.withLock {
        when (val state = store.read()) {
            is BootstrapStoreResult.Failure -> null

            is BootstrapStoreResult.Success -> when (val value = state.value) {
                is BootstrapState.Established -> value.workspace.context
                is BootstrapState.None -> null
                is BootstrapState.Candidate -> null
            }
        }
    }

    suspend fun readWorkspaceKey(): WorkspaceKeyRead = mutex.withLock {
        when (val gate = resolveUsableBinding()) {
            is BindingGate.Stop -> when (gate.result) {
                is BootstrapResult.Ready -> WorkspaceKeyRead.ActionRequired
                is BootstrapResult.WaitingForWorkspaceKey -> WorkspaceKeyRead.WaitingForWorkspaceKey
                is BootstrapResult.Retryable -> WorkspaceKeyRead.Retryable
                is BootstrapResult.ActionRequired -> WorkspaceKeyRead.ActionRequired
            }

            is BindingGate.Use -> keyReads.readEstablishedKey(gate.binding)
        }
    }

    private suspend fun resolveUsableBinding(): BindingGate {
        return when (val resolution = account.resolveBinding()) {
            is BindingResolution.Available -> BindingGate.Use(resolution.binding)
            is BindingResolution.Undetermined -> BindingGate.Stop(BootstrapResult.Retryable)
            is BindingResolution.Unavailable -> BindingGate.Stop(BootstrapResult.ActionRequired)
            is BindingResolution.Restricted -> BindingGate.Stop(BootstrapResult.ActionRequired)
        }
    }

    private suspend fun bootstrapWithBinding(binding: AccountBinding): BootstrapResult {
        return when (val state = store.read()) {
            is BootstrapStoreResult.Failure -> mapStoreFailure(state.reason)
            is BootstrapStoreResult.Success -> bootstrapWithState(binding, state.value)
        }
    }

    private suspend fun bootstrapWithState(
        binding: AccountBinding,
        state: BootstrapState
    ): BootstrapResult {
        return when (state) {
            is BootstrapState.None -> freshAttempt(binding)

            is BootstrapState.Candidate -> if (state.candidate.binding != binding) {
                BootstrapResult.ActionRequired
            } else {
                candidateAttempt(binding, state.candidate)
            }

            is BootstrapState.Established -> if (state.workspace.binding != binding) {
                BootstrapResult.ActionRequired
            } else {
                establishedAttempt(binding, state.workspace)
            }
        }
    }

    private suspend fun establishedAttempt(
        binding: AccountBinding,
        workspace: EstablishedWorkspace
    ): BootstrapResult {
        val zone = zones.ensureZone(binding, hasEstablished = true)
        if (zone is ZoneGate.Stop) {
            return zone.result
        }
        return when (val anchor = cloud.readAnchor(binding)) {
            is AnchorReadResult.Found -> {
                val expected = WorkspaceAnchor(
                    workspace.context.workspaceId,
                    workspace.context.transportEpochId,
                    workspace.context.keyEpochId,
                )
                if (anchor.anchor == expected) {
                    BootstrapResult.Ready(workspace.context)
                } else {
                    BootstrapResult.ActionRequired
                }
            }

            is AnchorReadResult.Missing -> {
                BootstrapResult.ActionRequired
            }

            is AnchorReadResult.Retryable -> {
                BootstrapResult.Retryable
            }

            is AnchorReadResult.UnknownOutcome -> {
                BootstrapResult.Retryable
            }

            is AnchorReadResult.IntegrityFailure -> {
                BootstrapResult.ActionRequired
            }

            is AnchorReadResult.AccountChanged -> {
                BootstrapResult.ActionRequired
            }
        }
    }

    private suspend fun freshAttempt(binding: AccountBinding): BootstrapResult {
        val zone = zones.ensureZone(binding, hasEstablished = false)
        if (zone is ZoneGate.Stop) {
            return zone.result
        }
        return when (val anchor = cloud.readAnchor(binding)) {
            is AnchorReadResult.Found -> anchors.adoptAnchorItem(binding, anchor.anchor)

            is AnchorReadResult.Missing -> when (val confirmed = candidates.createConfirmed(binding)) {
                is CandidateCreation.Confirmed -> persistCandidateAndAnchor(binding, confirmed.candidate)
                is CandidateCreation.Stop -> confirmed.result
            }

            is AnchorReadResult.Retryable -> BootstrapResult.Retryable

            is AnchorReadResult.UnknownOutcome -> BootstrapResult.Retryable

            is AnchorReadResult.IntegrityFailure -> BootstrapResult.ActionRequired

            is AnchorReadResult.AccountChanged -> BootstrapResult.ActionRequired
        }
    }

    private suspend fun candidateAttempt(
        binding: AccountBinding,
        candidate: PersistedCandidate
    ): BootstrapResult {
        val zone = zones.ensureZone(binding, hasEstablished = false)
        if (zone is ZoneGate.Stop) {
            return zone.result
        }
        return when (val anchor = cloud.readAnchor(binding)) {
            is AnchorReadResult.Found -> anchors.reconcileFoundAnchor(binding, anchor.anchor, candidate)

            is AnchorReadResult.Missing -> when (val confirmed = candidates.resumeConfirmed(binding, candidate)) {
                is CandidateCreation.Confirmed -> anchors.createAnchorFlow(
                    binding,
                    confirmed.candidate.anchor,
                    confirmed.candidate.account,
                    BootstrapResult.ActionRequired,
                )

                is CandidateCreation.Stop -> confirmed.result
            }

            is AnchorReadResult.Retryable -> BootstrapResult.Retryable

            is AnchorReadResult.UnknownOutcome -> BootstrapResult.Retryable

            is AnchorReadResult.IntegrityFailure -> BootstrapResult.ActionRequired

            is AnchorReadResult.AccountChanged -> BootstrapResult.ActionRequired
        }
    }

    private suspend fun persistCandidateAndAnchor(
        binding: AccountBinding,
        confirmed: ConfirmedCandidate
    ): BootstrapResult {
        val candidate = PersistedCandidate(
            confirmed.anchor.workspaceId,
            confirmed.anchor.transportEpochId,
            confirmed.anchor.keyEpochId,
            binding,
        )
        val persist = store.persistCandidate(candidate)
        if (persist is BootstrapStoreResult.Failure) {
            return mapStoreFailure(persist.reason)
        }
        return anchors.createAnchorFlow(
            binding,
            confirmed.anchor,
            confirmed.account,
            BootstrapResult.Retryable,
        )
    }
}
