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

    /**
     * Drops any retained removal resume state for [expectedBinding] after the
     * workspace is gone locally. Memory-only and infallible: performs no
     * provider operations and reports no outcome. Called exactly once per
     * completed removal, never while the established row is kept.
     */
    suspend fun clearRemovalResumeState(expectedBinding: AccountBinding)
}
