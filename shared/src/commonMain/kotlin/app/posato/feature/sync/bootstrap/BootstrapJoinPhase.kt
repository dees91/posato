package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.SyncContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class PendingWorkspaceJoin(
    val binding: AccountBinding,
    val anchor: WorkspaceAnchor,
)

internal enum class JoinCheckResult {
    UNCHANGED,
    WAITING,
    READY,
    LOCAL_ONLY,
    ACTION_REQUIRED,
    RETRYABLE,
    STATE_CHANGED,
}

internal class BootstrapJoinPhase(
    private val account: BootstrapAccountPort,
    private val cloud: BootstrapCloudPort,
    private val items: BootstrapItemCheck,
    private val store: BootstrapStore,
    private val mutex: Mutex,
) {
    private var pending: PendingWorkspaceJoin? = null

    fun clear() {
        pending = null
    }

    fun remember(
        binding: AccountBinding,
        anchor: WorkspaceAnchor
    ) {
        pending = PendingWorkspaceJoin(binding, anchor)
    }

    suspend fun hasPending(): Boolean {
        return mutex.withLock { pending != null }
    }

    suspend fun recheck(): JoinCheckResult {
        return mutex.withLock {
            val attempt = pending ?: return@withLock JoinCheckResult.STATE_CHANGED
            val result = try {
                check(attempt)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                JoinCheckResult.UNCHANGED
            }
            when (result) {
                JoinCheckResult.UNCHANGED, JoinCheckResult.WAITING, JoinCheckResult.RETRYABLE -> Unit
                else -> pending = null
            }
            result
        }
    }

    private suspend fun check(attempt: PendingWorkspaceJoin): JoinCheckResult {
        return when (val stored = store.read()) {
            is BootstrapStoreResult.Failure -> stored.reason.toJoinResult()

            is BootstrapStoreResult.Success -> when (val state = stored.value) {
                BootstrapState.None -> checkBinding(attempt)

                is BootstrapState.Candidate -> JoinCheckResult.STATE_CHANGED

                is BootstrapState.Established -> if (state.workspace == attempt.workspace()) {
                    JoinCheckResult.READY
                } else {
                    JoinCheckResult.ACTION_REQUIRED
                }
            }
        }
    }

    private suspend fun checkBinding(attempt: PendingWorkspaceJoin): JoinCheckResult {
        return when (val binding = account.resolveBinding()) {
            is BindingResolution.Available -> if (binding.binding == attempt.binding) {
                checkZone(attempt)
            } else {
                JoinCheckResult.ACTION_REQUIRED
            }

            BindingResolution.Undetermined -> JoinCheckResult.UNCHANGED

            BindingResolution.Unavailable, BindingResolution.Restricted -> JoinCheckResult.ACTION_REQUIRED
        }
    }

    private suspend fun checkZone(attempt: PendingWorkspaceJoin): JoinCheckResult {
        return when (cloud.fetchZone(attempt.binding)) {
            ZoneFetchResult.Found -> checkAnchor(attempt)
            ZoneFetchResult.Missing -> JoinCheckResult.LOCAL_ONLY
            ZoneFetchResult.AccountChanged -> JoinCheckResult.ACTION_REQUIRED
            ZoneFetchResult.Retryable, ZoneFetchResult.UnknownOutcome -> JoinCheckResult.UNCHANGED
        }
    }

    private suspend fun checkAnchor(attempt: PendingWorkspaceJoin): JoinCheckResult {
        return when (val anchor = cloud.readAnchor(attempt.binding)) {
            is AnchorReadResult.Found -> if (anchor.anchor == attempt.anchor) {
                checkItem(attempt)
            } else {
                JoinCheckResult.ACTION_REQUIRED
            }

            AnchorReadResult.Missing -> JoinCheckResult.LOCAL_ONLY

            AnchorReadResult.AccountChanged, AnchorReadResult.IntegrityFailure -> JoinCheckResult.ACTION_REQUIRED

            AnchorReadResult.Retryable, AnchorReadResult.UnknownOutcome -> JoinCheckResult.UNCHANGED
        }
    }

    private suspend fun checkItem(attempt: PendingWorkspaceJoin): JoinCheckResult {
        val selector = items.accountFor(attempt.anchor.workspaceId) ?: return JoinCheckResult.ACTION_REQUIRED
        return when (val item = items.check(attempt.binding, selector)) {
            is ItemCheck.Valid -> {
                val matches = items.matchesAnchor(item.decoded, attempt.anchor)
                item.decoded.clear()
                if (matches) commit(attempt) else JoinCheckResult.ACTION_REQUIRED
            }

            ItemCheck.Missing -> {
                JoinCheckResult.WAITING
            }

            ItemCheck.Retryable -> {
                JoinCheckResult.UNCHANGED
            }

            ItemCheck.ActionRequired -> {
                JoinCheckResult.ACTION_REQUIRED
            }
        }
    }

    private suspend fun commit(attempt: PendingWorkspaceJoin): JoinCheckResult {
        return when (val result = store.commitEstablished(attempt.workspace())) {
            is BootstrapStoreResult.Success -> JoinCheckResult.READY
            is BootstrapStoreResult.Failure -> result.reason.toJoinResult()
        }
    }
}

private fun PendingWorkspaceJoin.workspace(): EstablishedWorkspace {
    return EstablishedWorkspace(
        SyncContext(anchor.workspaceId, anchor.transportEpochId, anchor.keyEpochId),
        binding,
    )
}

private fun BootstrapStoreFailure.toJoinResult(): JoinCheckResult {
    return when (this) {
        BootstrapStoreFailure.CORRUPTION -> JoinCheckResult.ACTION_REQUIRED
        BootstrapStoreFailure.STORAGE_FAILURE -> JoinCheckResult.RETRYABLE
    }
}
