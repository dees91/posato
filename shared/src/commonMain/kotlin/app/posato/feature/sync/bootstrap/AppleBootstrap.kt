package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.SyncContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AppleBootstrap(
    private val coordinator: BootstrapCoordinator,
    internal val backgroundDispatcher: CoroutineDispatcher,
) {
    suspend fun syncWithIcloud(): BootstrapResult {
        return flight.withLock {
            withContext(backgroundDispatcher) {
                try {
                    coordinator.bootstrap()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    BootstrapResult.Retryable
                }
            }
        }
    }

    suspend fun establishedContext(): SyncContext? {
        return flight.withLock {
            withContext(backgroundDispatcher) {
                coordinator.establishedContext()
            }
        }
    }

    /**
     * Runs [action] under the synchronization lock only while this device has no established workspace, and
     * returns null otherwise. Used only by the verification seams of the ADR 0007 amendment of 2026-10-09.
     */
    suspend fun <T> whileLocalOnly(action: suspend () -> T): T? {
        return flight.withLock {
            withContext(backgroundDispatcher) {
                if (coordinator.checkEstablished().status == EstablishedStatus.LOCAL_ONLY) action() else null
            }
        }
    }

    companion object {
        internal val flight = Mutex()
    }
}
