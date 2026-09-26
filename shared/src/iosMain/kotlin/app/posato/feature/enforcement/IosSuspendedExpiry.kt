package app.posato.feature.enforcement

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

public enum class IosSuspendedExpiryOutcome {
    SCHEDULED,
    CANCELLED,
    BELOW_PLATFORM_MINIMUM,
    AUTHORIZATION_REQUIRED,
    UNAVAILABLE,
    PLATFORM_FAILURE,
}

public enum class IosExpiryReconciliation {
    EXPIRED,
    UNKNOWN,
}

public class IosSuspendedExpiryRequest(
    public val sessionId: String,
    public val startEpochSeconds: Long,
    public val endEpochSeconds: Long,
) {
    override fun equals(other: Any?): Boolean {
        return other is IosSuspendedExpiryRequest &&
            sessionId == other.sessionId &&
            startEpochSeconds == other.startEpochSeconds &&
            endEpochSeconds == other.endEpochSeconds
    }

    override fun hashCode(): Int {
        return 31 * (31 * sessionId.hashCode() + startEpochSeconds.hashCode()) + endEpochSeconds.hashCode()
    }

    override fun toString(): String {
        return "IosSuspendedExpiryRequest(redacted)"
    }
}

public interface IosSuspendedExpiryProvider {
    public fun schedule(
        request: IosSuspendedExpiryRequest,
        completion: (IosSuspendedExpiryOutcome) -> Unit,
    )

    public fun cancel(handler: (IosSuspendedExpiryOutcome) -> Unit)

    public fun isScheduled(
        sessionId: String,
        handler: (Boolean) -> Unit,
    )

    public fun readReconciliation(
        sessionId: String,
        handler: (IosExpiryReconciliation) -> Unit,
    )

    public fun acknowledgeReconciliation(
        sessionId: String,
        handler: (Boolean) -> Unit,
    )

    public fun displacedClearedSessionId(
        currentSessionId: String,
        handler: (ExpiryDisplacement) -> Unit,
    )
}

public class IosSuspendedExpiry(
    private val provider: IosSuspendedExpiryProvider,
) {
    public suspend fun schedule(request: IosSuspendedExpiryRequest): IosSuspendedExpiryOutcome {
        return suspendCoroutine { continuation ->
            provider.schedule(request) { outcome -> continuation.resume(outcome) }
        }
    }

    public suspend fun cancel(): IosSuspendedExpiryOutcome {
        return suspendCoroutine { continuation ->
            provider.cancel { outcome -> continuation.resume(outcome) }
        }
    }

    public suspend fun isScheduled(sessionId: String): Boolean {
        return suspendCoroutine { continuation ->
            provider.isScheduled(sessionId) { scheduled -> continuation.resume(scheduled) }
        }
    }

    public suspend fun readReconciliation(sessionId: String): IosExpiryReconciliation {
        return suspendCoroutine { continuation ->
            provider.readReconciliation(sessionId) { reconciliation -> continuation.resume(reconciliation) }
        }
    }

    public suspend fun acknowledgeReconciliation(sessionId: String): Boolean {
        return suspendCoroutine { continuation ->
            provider.acknowledgeReconciliation(sessionId) { acknowledged -> continuation.resume(acknowledged) }
        }
    }

    public suspend fun displacedClearedSessionId(currentSessionId: String): ExpiryDisplacement {
        return suspendCoroutine { continuation ->
            provider.displacedClearedSessionId(currentSessionId) { displaced -> continuation.resume(displaced) }
        }
    }
}
