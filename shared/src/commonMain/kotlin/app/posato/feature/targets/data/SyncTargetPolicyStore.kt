package app.posato.feature.targets.data

import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.targets.domain.TargetPolicy

internal class SyncTargetPolicyStore(
    private val local: LocalTargetPolicyStore,
    private val sync: AppleSync,
) : LocalTargetPolicyStore {
    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return local.read()
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy
    ): LocalPolicyResult<LocalTargetPolicyState> {
        val before = when (val read = local.read()) {
            is LocalPolicyResult.Success -> read.value
            is LocalPolicyResult.Failure -> return read
        }
        val result = local.replace(expectedRevision, policy)
        if (result is LocalPolicyResult.Success) {
            sync.recordDomainChanges(before.policy, result.value.policy)
        }
        return result
    }
}
