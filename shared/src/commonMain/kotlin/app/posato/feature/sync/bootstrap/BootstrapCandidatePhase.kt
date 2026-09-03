package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId

internal data class ConfirmedCandidate(
    val anchor: WorkspaceAnchor,
    val account: KeyAccount
) {
    override fun toString(): String {
        return "ConfirmedCandidate(redacted)"
    }
}

internal sealed interface CandidateCreation {
    data class Confirmed(
        val candidate: ConfirmedCandidate
    ) : CandidateCreation

    data class Stop(
        val result: BootstrapResult
    ) : CandidateCreation
}

private data class MintedCandidate(
    val workspaceId: WorkspaceId,
    val transportEpochId: TransportEpochId,
    val keyEpochId: KeyEpochId,
    val keyBytes: ByteArray
) {
    override fun toString(): String {
        return "MintedCandidate(redacted)"
    }
}

internal class BootstrapCandidatePhase(
    private val keys: BootstrapKeyPort,
    private val crypto: SyncCryptoProvider,
    private val items: BootstrapItemCheck
) {
    suspend fun createConfirmed(binding: AccountBinding): CandidateCreation {
        val minted = mintFresh() ?: return CandidateCreation.Stop(BootstrapResult.Retryable)
        return try {
            confirmCreatedItem(binding, minted)
        } finally {
            minted.keyBytes.fill(0)
        }
    }

    suspend fun resumeConfirmed(
        binding: AccountBinding,
        candidate: PersistedCandidate
    ): CandidateCreation {
        val account = items.accountFor(candidate.workspaceId)
            ?: return CandidateCreation.Stop(BootstrapResult.ActionRequired)
        return when (val check = items.check(binding, account)) {
            is ItemCheck.Valid -> {
                val anchor = WorkspaceAnchor(candidate.workspaceId, candidate.transportEpochId, candidate.keyEpochId)
                val matches = items.matchesAnchor(check.decoded, anchor)
                check.decoded.clear()
                if (!matches) {
                    CandidateCreation.Stop(BootstrapResult.ActionRequired)
                } else {
                    CandidateCreation.Confirmed(ConfirmedCandidate(anchor, account))
                }
            }

            is ItemCheck.Missing -> {
                CandidateCreation.Stop(BootstrapResult.ActionRequired)
            }

            is ItemCheck.Retryable -> {
                CandidateCreation.Stop(BootstrapResult.Retryable)
            }

            is ItemCheck.ActionRequired -> {
                CandidateCreation.Stop(BootstrapResult.ActionRequired)
            }
        }
    }

    private suspend fun confirmCreatedItem(
        binding: AccountBinding,
        minted: MintedCandidate
    ): CandidateCreation {
        val account = items.accountFor(minted.workspaceId)
            ?: return CandidateCreation.Stop(BootstrapResult.ActionRequired)
        val encoded = BootstrapEncoding.encodeKeyItem(
            minted.workspaceId,
            minted.transportEpochId,
            minted.keyEpochId,
            minted.keyBytes,
        ) ?: return CandidateCreation.Stop(BootstrapResult.ActionRequired)
        val created = mapCreate(keys.createItem(binding, account, encoded))
        if (created != null) {
            return created
        }
        return when (val check = items.check(binding, account)) {
            is ItemCheck.Valid -> {
                val anchor = WorkspaceAnchor(minted.workspaceId, minted.transportEpochId, minted.keyEpochId)
                val matches = items.matchesAnchor(check.decoded, anchor) &&
                    BootstrapEncoding.constantTimeEquals(check.decoded.workspaceKey, minted.keyBytes)
                check.decoded.clear()
                if (!matches) {
                    CandidateCreation.Stop(BootstrapResult.ActionRequired)
                } else {
                    CandidateCreation.Confirmed(ConfirmedCandidate(anchor, account))
                }
            }

            is ItemCheck.Missing -> {
                CandidateCreation.Stop(BootstrapResult.Retryable)
            }

            is ItemCheck.Retryable -> {
                CandidateCreation.Stop(BootstrapResult.Retryable)
            }

            is ItemCheck.ActionRequired -> {
                CandidateCreation.Stop(BootstrapResult.ActionRequired)
            }
        }
    }

    private fun mapCreate(create: KeyItemCreateResult): CandidateCreation.Stop? {
        return when (create) {
            is KeyItemCreateResult.Created -> null
            is KeyItemCreateResult.AlreadyExists -> null
            is KeyItemCreateResult.Retryable -> CandidateCreation.Stop(BootstrapResult.Retryable)
            is KeyItemCreateResult.UnknownOutcome -> CandidateCreation.Stop(BootstrapResult.Retryable)
            is KeyItemCreateResult.IntegrityFailure -> CandidateCreation.Stop(BootstrapResult.ActionRequired)
            is KeyItemCreateResult.AccountChanged -> CandidateCreation.Stop(BootstrapResult.ActionRequired)
        }
    }

    private fun mintFresh(): MintedCandidate? {
        val workspaceId = mintIdentifier() ?: return null
        val transportEpochId = mintIdentifier() ?: return null
        val keyEpochId = mintIdentifier() ?: return null
        val keyBytes = crypto.randomBytes(WORKSPACE_KEY_BYTES)
        if (keyBytes == null || keyBytes.size != WORKSPACE_KEY_BYTES) {
            keyBytes?.fill(0)
            return null
        }
        return MintedCandidate(
            WorkspaceId(workspaceId),
            TransportEpochId(transportEpochId),
            KeyEpochId(keyEpochId),
            keyBytes,
        )
    }

    private fun mintIdentifier(): SyncIdentifier? {
        val bytes = crypto.randomBytes(SyncFormatLimits.IDENTIFIER_BYTES) ?: return null
        return BootstrapEncoding.applyUuidVersionFour(bytes)
    }
}
