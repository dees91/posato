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

    companion object {
        internal val flight = Mutex()
    }
}
