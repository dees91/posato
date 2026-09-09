package app.posato.feature.sync.mailbox

import app.posato.feature.sync.bootstrap.AccountBinding

internal interface MailboxPort {
    suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray
    ): BundleSaveResult

    suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor
    ): ChangeFetchResult

    suspend fun deleteZoneAndVerifyAbsent(expectedBinding: AccountBinding): ZoneDeleteResult
}
