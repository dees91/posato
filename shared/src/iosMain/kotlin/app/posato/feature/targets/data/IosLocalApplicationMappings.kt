package app.posato.feature.targets.data

import app.posato.feature.onboarding.ApplicationAccessResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class IosApplicationMappingsAccess { READY, AUTHORIZATION_REQUIRED, AUTHORIZATION_DENIED, RESTRICTED, UNAVAILABLE }

enum class IosApplicationMappingsOutcome {
    SUCCESS,
    CANCELLED,
    ACCESS_CHANGED,
    INVALID_SELECTION,
    CAPACITY,
    PICKER_FAILURE,
    STORAGE_FAILURE,
    CORRUPTION,
    UNAVAILABLE,
}

class IosApplicationMappingReference(
    val identifier: String,
) {
    override fun toString(): String {
        return "IosApplicationMappingReference(redacted)"
    }
}

class IosApplicationMappingsResponse(
    val outcome: IosApplicationMappingsOutcome,
    val access: IosApplicationMappingsAccess,
    val mappings: List<IosApplicationMappingReference>,
) {
    override fun toString(): String {
        return "IosApplicationMappingsResponse(redacted)"
    }
}

interface IosApplicationMappingsOperation {
    fun cancel()
}

interface IosApplicationMappingsObservation {
    fun cancel()
}

interface IosApplicationMappingsProvider {
    fun load(completion: (IosApplicationMappingsResponse) -> Unit)

    fun choose(completion: (IosApplicationMappingsResponse) -> Unit): IosApplicationMappingsOperation

    fun requestAuthorization(completion: (IosApplicationMappingsResponse) -> Unit): IosApplicationMappingsOperation

    fun remove(
        identifier: String,
        completion: (IosApplicationMappingsResponse) -> Unit,
    )

    fun clear(completion: (IosApplicationMappingsResponse) -> Unit)

    fun observeInvalidations(handler: () -> Unit): IosApplicationMappingsObservation
}

internal class IosLocalApplicationMappings(
    private val provider: IosApplicationMappingsProvider,
) : LocalApplicationMappings {
    private val operationMutex = Mutex()

    override val invalidations: Flow<Unit> = callbackFlow {
        val observation = provider.observeInvalidations { trySend(Unit) }
        awaitClose(observation::cancel)
    }

    override suspend fun load(): LocalApplicationMappingsLoadResult {
        return operationMutex.withLock {
            val response = suspendCoroutine<IosApplicationMappingsResponse> { continuation ->
                provider.load { result -> continuation.resume(result) }
            }
            response.toLoadResult()
        }
    }

    override suspend fun chooseApplications(): LocalApplicationSelectionResult {
        return operationMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                val operation = provider.choose { response ->
                    if (continuation.isActive) {
                        continuation.resume(response.toSelectionResult())
                    }
                }
                continuation.invokeOnCancellation { operation.cancel() }
            }
        }
    }

    suspend fun requestAuthorization(): ApplicationAccessResult {
        return operationMutex.withLock {
            val response = suspendCancellableCoroutine { continuation ->
                val operation = provider.requestAuthorization { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                continuation.invokeOnCancellation { operation.cancel() }
            }
            response.toAccessResult()
        }
    }

    override suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult {
        return operationMutex.withLock {
            val response = suspendCoroutine<IosApplicationMappingsResponse> { continuation ->
                provider.remove(mappingId.canonicalValue) { result -> continuation.resume(result) }
            }
            response.toRemovalResult()
        }
    }

    override suspend fun clear(): LocalApplicationRemovalResult {
        return operationMutex.withLock {
            val response = suspendCoroutine<IosApplicationMappingsResponse> { continuation ->
                provider.clear { result -> continuation.resume(result) }
            }
            response.toRemovalResult()
        }
    }
}

private fun IosApplicationMappingsResponse.toLoadResult(): LocalApplicationMappingsLoadResult {
    val snapshot = restoreSnapshot() ?: return LocalApplicationMappingsLoadResult.Failure(
        LocalApplicationMappingsLoadFailure.CORRUPTION,
    )
    return when (outcome) {
        IosApplicationMappingsOutcome.SUCCESS -> access.toCommonAccess()?.let { commonAccess ->
            LocalApplicationMappingsLoadResult.Success(snapshot, commonAccess)
        } ?: LocalApplicationMappingsLoadResult.Unavailable(snapshot)

        IosApplicationMappingsOutcome.UNAVAILABLE -> LocalApplicationMappingsLoadResult.Unavailable(snapshot)

        IosApplicationMappingsOutcome.CORRUPTION -> LocalApplicationMappingsLoadResult.Failure(
            LocalApplicationMappingsLoadFailure.CORRUPTION,
        )

        else -> LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE)
    }
}

private fun IosApplicationMappingsResponse.toSelectionResult(): LocalApplicationSelectionResult {
    return when (outcome) {
        IosApplicationMappingsOutcome.SUCCESS -> {
            restoreSnapshot()?.let(LocalApplicationSelectionResult::Success)
                ?: LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.STORAGE)
        }

        IosApplicationMappingsOutcome.CANCELLED -> {
            LocalApplicationSelectionResult.Cancelled
        }

        IosApplicationMappingsOutcome.ACCESS_CHANGED -> {
            val snapshot = restoreSnapshot()
            val commonAccess = access.toCommonAccess()
            if (snapshot != null && commonAccess != null) {
                LocalApplicationSelectionResult.AccessChanged(snapshot, commonAccess)
            } else {
                LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.STORAGE)
            }
        }

        IosApplicationMappingsOutcome.CAPACITY -> {
            LocalApplicationSelectionResult.Rejected(LocalApplicationSelectionRejection.CAPACITY)
        }

        IosApplicationMappingsOutcome.INVALID_SELECTION -> {
            LocalApplicationSelectionResult.Rejected(LocalApplicationSelectionRejection.UNSUPPORTED)
        }

        IosApplicationMappingsOutcome.UNAVAILABLE -> {
            LocalApplicationSelectionResult.Unavailable
        }

        IosApplicationMappingsOutcome.PICKER_FAILURE -> {
            LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.PICKER)
        }

        else -> {
            LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.STORAGE)
        }
    }
}

private fun IosApplicationMappingsResponse.toAccessResult(): ApplicationAccessResult {
    return when (outcome) {
        IosApplicationMappingsOutcome.SUCCESS,
        IosApplicationMappingsOutcome.CANCELLED,
        IosApplicationMappingsOutcome.ACCESS_CHANGED -> {
            val commonAccess = access.toCommonAccess()
            if (commonAccess != null) {
                ApplicationAccessResult.Determined(commonAccess)
            } else {
                ApplicationAccessResult.Unavailable
            }
        }

        IosApplicationMappingsOutcome.UNAVAILABLE -> ApplicationAccessResult.Unavailable

        else -> ApplicationAccessResult.Failed
    }
}

private fun IosApplicationMappingsResponse.toRemovalResult(): LocalApplicationRemovalResult {
    return when (outcome) {
        IosApplicationMappingsOutcome.SUCCESS -> restoreSnapshot()?.let(LocalApplicationRemovalResult::Success)
            ?: LocalApplicationRemovalResult.Failure(LocalApplicationRemovalFailure.STORAGE)

        IosApplicationMappingsOutcome.UNAVAILABLE -> LocalApplicationRemovalResult.Unavailable

        else -> LocalApplicationRemovalResult.Failure(LocalApplicationRemovalFailure.STORAGE)
    }
}

private fun IosApplicationMappingsResponse.restoreSnapshot(): LocalApplicationMappingsSnapshot? {
    val restored = mappings.map { reference ->
        val id = LocalApplicationMappingId.restore(reference.identifier) ?: return null
        LocalApplicationMapping.restoreOpaque(id)
    }

    return LocalApplicationMappingsSnapshot.restore(restored)
}

private fun IosApplicationMappingsAccess.toCommonAccess(): LocalApplicationMappingsAccess? {
    return when (this) {
        IosApplicationMappingsAccess.READY -> LocalApplicationMappingsAccess.READY
        IosApplicationMappingsAccess.AUTHORIZATION_REQUIRED -> LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED
        IosApplicationMappingsAccess.AUTHORIZATION_DENIED -> LocalApplicationMappingsAccess.AUTHORIZATION_DENIED
        IosApplicationMappingsAccess.RESTRICTED -> LocalApplicationMappingsAccess.RESTRICTED
        IosApplicationMappingsAccess.UNAVAILABLE -> null
    }
}
