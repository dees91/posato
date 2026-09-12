package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.MailboxPort

internal class BootstrapMissingAnchorPhase(
    private val cloud: BootstrapCloudPort,
    private val mailbox: MailboxPort,
    private val candidates: BootstrapCandidatePhase,
    private val anchors: BootstrapAnchorPhase,
    private val joins: BootstrapJoinPhase,
    private val store: BootstrapStore
) {
    suspend fun resolve(binding: AccountBinding): BootstrapResult {
        return when (mailbox.sweepBundlesIfAnchorMissing(binding)) {
            is BundleSweepResult.Swept -> {
                mintFresh(binding)
            }

            is BundleSweepResult.AnchorPresent -> {
                adoptAppearing(binding)
            }

            is BundleSweepResult.Retryable, is BundleSweepResult.UnknownOutcome -> {
                BootstrapResult.Retryable
            }

            is BundleSweepResult.AccountChanged -> {
                BootstrapResult.ActionRequired
            }
        }
    }

    private suspend fun mintFresh(binding: AccountBinding): BootstrapResult {
        return when (val confirmed = candidates.createConfirmed(binding)) {
            is CandidateCreation.Confirmed -> {
                persistCandidateAndAnchor(binding, confirmed.candidate)
            }

            is CandidateCreation.Stop -> {
                confirmed.result
            }
        }
    }

    private suspend fun adoptAppearing(binding: AccountBinding): BootstrapResult {
        return when (val reread = cloud.readAnchor(binding)) {
            is AnchorReadResult.Found -> {
                adoptJoinableAnchor(store, anchors, joins, binding, reread.anchor)
            }

            is AnchorReadResult.Retryable, is AnchorReadResult.UnknownOutcome -> {
                BootstrapResult.Retryable
            }

            is AnchorReadResult.Missing, is AnchorReadResult.IntegrityFailure,
            is AnchorReadResult.AccountChanged -> {
                BootstrapResult.ActionRequired
            }
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
