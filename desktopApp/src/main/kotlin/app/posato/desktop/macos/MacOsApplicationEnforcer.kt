package app.posato.desktop.macos

import app.posato.feature.targets.data.LocalApplicationMappingId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface ApplicationEnforcementResult {
    data class Active(
        val acceptedCount: Int,
    ) : ApplicationEnforcementResult {
        override fun toString(): String {
            return "ApplicationEnforcementResult.Active(redacted)"
        }
    }

    data class Failed(
        val result: HelperResult,
    ) : ApplicationEnforcementResult {
        override fun toString(): String {
            return "ApplicationEnforcementResult.Failed(redacted)"
        }
    }
}

internal class MacOsApplicationEnforcer(
    private val commands: MacOsApplicationCommands,
    private val requirements: suspend (List<LocalApplicationMappingId>) -> List<ByteArray>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun start(
        mappingIds: List<LocalApplicationMappingId>,
        sessionEndEpochMilliseconds: Long? = null,
    ): ApplicationEnforcementResult {
        return withContext(ioDispatcher) {
            if (mappingIds.isEmpty()) {
                ApplicationEnforcementResult.Failed(invalidInput())
            } else {
                activate(mappingIds, sessionEndEpochMilliseconds)
            }
        }
    }

    suspend fun clear(): HelperResult {
        return withContext(ioDispatcher) {
            commands.configureApplications(emptyList(), null).result
        }
    }

    private suspend fun activate(
        mappingIds: List<LocalApplicationMappingId>,
        sessionEndEpochMilliseconds: Long?,
    ): ApplicationEnforcementResult {
        val blobs = try {
            requirements(mappingIds).also { resolved ->
                require(resolved.size == mappingIds.size)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IllegalArgumentException) {
            return ApplicationEnforcementResult.Failed(invalidInput())
        } catch (_: Exception) {
            return ApplicationEnforcementResult.Failed(storageFailure())
        }
        val configured = commands.configureApplications(blobs, sessionEndEpochMilliseconds)
        if (configured.result.outcome != HelperResult.Outcome.Success || configured.acceptedCount != blobs.size) {
            return ApplicationEnforcementResult.Failed(configured.result)
        }
        return ApplicationEnforcementResult.Active(configured.acceptedCount)
    }

    private fun invalidInput(): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.Failure,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.InvalidInput,
        )
    }

    private fun storageFailure(): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.Failure,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.Storage,
        )
    }
}
