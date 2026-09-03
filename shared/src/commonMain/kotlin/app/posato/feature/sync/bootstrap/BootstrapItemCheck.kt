package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.WorkspaceId

internal class BootstrapItemCheck(
    private val keys: BootstrapKeyPort
) {
    suspend fun check(
        binding: AccountBinding,
        account: KeyAccount
    ): ItemCheck {
        return when (val read = keys.readItem(binding, account)) {
            is KeyItemReadResult.Found -> when (val decoded = BootstrapEncoding.decodeKeyItem(read.value)) {
                null -> ItemCheck.ActionRequired
                else -> ItemCheck.Valid(decoded)
            }

            is KeyItemReadResult.Missing -> ItemCheck.Missing

            is KeyItemReadResult.Retryable -> ItemCheck.Retryable

            is KeyItemReadResult.UnknownOutcome -> ItemCheck.Retryable

            is KeyItemReadResult.IntegrityFailure -> ItemCheck.ActionRequired

            is KeyItemReadResult.AccountChanged -> ItemCheck.ActionRequired
        }
    }

    fun matchesAnchor(
        decoded: DecodedKeyItem,
        anchor: WorkspaceAnchor
    ): Boolean {
        return decoded.workspaceId == anchor.workspaceId &&
            decoded.transportEpochId == anchor.transportEpochId &&
            decoded.keyEpochId == anchor.keyEpochId
    }

    fun accountFor(workspaceId: WorkspaceId): KeyAccount? {
        return KeyAccount.fromText(BootstrapEncoding.identifierToAccountText(workspaceId.value))
    }
}
