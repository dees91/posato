package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.SyncContext

internal class BootstrapAnchorPhase(
    private val cloud: BootstrapCloudPort,
    private val keys: BootstrapKeyPort,
    private val items: BootstrapItemCheck,
    private val store: BootstrapStore
) {
    suspend fun createAnchorFlow(
        binding: AccountBinding,
        anchor: WorkspaceAnchor,
        ownAccount: KeyAccount,
        missingOwnItem: BootstrapResult
    ): BootstrapResult {
        return when (val create = cloud.createAnchor(binding, anchor)) {
            is AnchorCreateResult.Created -> confirmOwnAnchor(binding, anchor, missingOwnItem)
            is AnchorCreateResult.Conflict -> reconcileAnchorConflict(binding, anchor, ownAccount, missingOwnItem)
            is AnchorCreateResult.Retryable -> BootstrapResult.Retryable
            is AnchorCreateResult.UnknownOutcome -> BootstrapResult.Retryable
            is AnchorCreateResult.IntegrityFailure -> BootstrapResult.ActionRequired
            is AnchorCreateResult.AccountChanged -> BootstrapResult.ActionRequired
        }
    }

    suspend fun reconcileFoundAnchor(
        binding: AccountBinding,
        anchor: WorkspaceAnchor,
        candidate: PersistedCandidate
    ): BootstrapResult {
        if (anchor.workspaceId == candidate.workspaceId) {
            return confirmOwnAnchor(
                binding,
                WorkspaceAnchor(candidate.workspaceId, candidate.transportEpochId, candidate.keyEpochId),
                BootstrapResult.ActionRequired,
            )
        }
        val account = items.accountFor(candidate.workspaceId) ?: return BootstrapResult.ActionRequired
        return adoptWinner(binding, anchor, account)
    }

    suspend fun adoptAnchorItem(
        binding: AccountBinding,
        anchor: WorkspaceAnchor
    ): BootstrapResult {
        val account = items.accountFor(anchor.workspaceId) ?: return BootstrapResult.ActionRequired
        return when (val check = items.check(binding, account)) {
            is ItemCheck.Valid -> {
                val result = commitIfMatches(binding, anchor, check.decoded)
                check.decoded.clear()
                result
            }

            is ItemCheck.Missing -> {
                BootstrapResult.WaitingForWorkspaceKey
            }

            is ItemCheck.Retryable -> {
                BootstrapResult.Retryable
            }

            is ItemCheck.ActionRequired -> {
                BootstrapResult.ActionRequired
            }
        }
    }

    private suspend fun confirmOwnAnchor(
        binding: AccountBinding,
        anchor: WorkspaceAnchor,
        missingOwnItem: BootstrapResult
    ): BootstrapResult {
        return when (val read = cloud.readAnchor(binding)) {
            is AnchorReadResult.Found -> if (read.anchor == anchor) {
                confirmOwnItem(binding, anchor, missingOwnItem)
            } else {
                BootstrapResult.ActionRequired
            }

            is AnchorReadResult.Missing -> BootstrapResult.Retryable

            is AnchorReadResult.Retryable -> BootstrapResult.Retryable

            is AnchorReadResult.UnknownOutcome -> BootstrapResult.Retryable

            is AnchorReadResult.IntegrityFailure -> BootstrapResult.ActionRequired

            is AnchorReadResult.AccountChanged -> BootstrapResult.ActionRequired
        }
    }

    private suspend fun confirmOwnItem(
        binding: AccountBinding,
        anchor: WorkspaceAnchor,
        missingOwnItem: BootstrapResult
    ): BootstrapResult {
        val account = items.accountFor(anchor.workspaceId) ?: return BootstrapResult.ActionRequired
        return when (val check = items.check(binding, account)) {
            is ItemCheck.Valid -> {
                val matches = items.matchesAnchor(check.decoded, anchor)
                check.decoded.clear()
                if (!matches) {
                    BootstrapResult.ActionRequired
                } else {
                    commitEstablishedAsReady(binding, anchor)
                }
            }

            is ItemCheck.Missing -> {
                missingOwnItem
            }

            is ItemCheck.Retryable -> {
                BootstrapResult.Retryable
            }

            is ItemCheck.ActionRequired -> {
                BootstrapResult.ActionRequired
            }
        }
    }

    private suspend fun reconcileAnchorConflict(
        binding: AccountBinding,
        anchor: WorkspaceAnchor,
        ownAccount: KeyAccount,
        missingOwnItem: BootstrapResult
    ): BootstrapResult {
        return when (val read = cloud.readAnchor(binding)) {
            is AnchorReadResult.Found -> if (read.anchor == anchor) {
                confirmOwnAnchor(binding, anchor, missingOwnItem)
            } else {
                adoptWinner(binding, read.anchor, ownAccount)
            }

            is AnchorReadResult.Missing -> BootstrapResult.Retryable

            is AnchorReadResult.Retryable -> BootstrapResult.Retryable

            is AnchorReadResult.UnknownOutcome -> BootstrapResult.Retryable

            is AnchorReadResult.IntegrityFailure -> BootstrapResult.ActionRequired

            is AnchorReadResult.AccountChanged -> BootstrapResult.ActionRequired
        }
    }

    private suspend fun adoptWinner(
        binding: AccountBinding,
        winner: WorkspaceAnchor,
        ownAccount: KeyAccount
    ): BootstrapResult {
        val refused = refuseIfRemoved(store, winner.workspaceId)
        if (refused != null) {
            return refused
        }
        val cleanup = mapDelete(keys.deleteItemAndVerifyAbsent(binding, ownAccount))
        if (cleanup != null) {
            return cleanup
        }
        return adoptAnchorItem(binding, winner)
    }

    private fun mapDelete(delete: KeyItemDeleteResult): BootstrapResult? {
        return when (delete) {
            is KeyItemDeleteResult.DeletedAndAbsent -> null
            is KeyItemDeleteResult.Retryable -> BootstrapResult.Retryable
            is KeyItemDeleteResult.UnknownOutcome -> BootstrapResult.Retryable
            is KeyItemDeleteResult.IntegrityFailure -> BootstrapResult.ActionRequired
            is KeyItemDeleteResult.AccountChanged -> BootstrapResult.ActionRequired
        }
    }

    private suspend fun commitIfMatches(
        binding: AccountBinding,
        anchor: WorkspaceAnchor,
        decoded: DecodedKeyItem
    ): BootstrapResult {
        if (!items.matchesAnchor(decoded, anchor)) {
            return BootstrapResult.ActionRequired
        }
        return commitEstablishedAsReady(binding, anchor)
    }

    private suspend fun commitEstablishedAsReady(
        binding: AccountBinding,
        anchor: WorkspaceAnchor
    ): BootstrapResult {
        val context = SyncContext(anchor.workspaceId, anchor.transportEpochId, anchor.keyEpochId)
        return when (val commit = store.commitEstablished(EstablishedWorkspace(context, binding))) {
            is BootstrapStoreResult.Success -> BootstrapResult.Ready(context)
            is BootstrapStoreResult.Failure -> mapStoreFailure(commit.reason)
        }
    }
}
