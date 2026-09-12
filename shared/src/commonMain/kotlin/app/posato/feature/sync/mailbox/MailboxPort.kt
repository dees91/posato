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

    suspend fun deleteWorkspaceRecords(expectedBinding: AccountBinding): RecordDeleteResult

    suspend fun sweepBundlesIfAnchorMissing(expectedBinding: AccountBinding): BundleSweepResult
}
