package app.posato.feature.targets.data

import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SyncTargetPolicyStore(
    private val local: LocalTargetPolicyStore,
    private val sync: AppleSync,
) : LocalTargetPolicyStore {
    private val saves = Mutex()

    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return local.read()
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return saves.withLock {
            val before = when (val read = local.read()) {
                is LocalPolicyResult.Success -> read.value
                is LocalPolicyResult.Failure -> return@withLock read
            }
            val workspace = sync.captureWorkspace()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val result = local.replace(expectedRevision, policy)
                if (result is LocalPolicyResult.Success) {
                    sync.enqueueDomainChanges(workspace, before.policy, result.value.policy)
                }
                result
            }
        }
    }
}
