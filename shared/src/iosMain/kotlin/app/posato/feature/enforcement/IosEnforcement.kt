package app.posato.feature.enforcement

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

public enum class IosEnforcementOutcome {
    APPLIED,
    CLEARED,
    NOTHING_TO_ENFORCE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZATION_DENIED,
    RESTRICTED,
    UNAVAILABLE,
    SELECTION_MISSING,
    PLATFORM_FAILURE,
}

public class IosEnforcementRequest(
    public val domains: List<String>,
    public val mappingIds: List<String>,
) {
    override fun equals(other: Any?): Boolean {
        return other is IosEnforcementRequest && domains == other.domains && mappingIds == other.mappingIds
    }

    override fun hashCode(): Int {
        return 31 * domains.hashCode() + mappingIds.hashCode()
    }

    override fun toString(): String {
        return "IosEnforcementRequest(redacted)"
    }
}

public interface IosEnforcementProvider {
    public fun apply(
        request: IosEnforcementRequest,
        completion: (IosEnforcementOutcome) -> Unit,
    )

    public fun clear(handler: (IosEnforcementOutcome) -> Unit)
}

public class IosEnforcement(
    private val provider: IosEnforcementProvider,
) {
    public suspend fun apply(request: IosEnforcementRequest): IosEnforcementOutcome {
        if (request.domains.isEmpty() && request.mappingIds.isEmpty()) {
            return IosEnforcementOutcome.NOTHING_TO_ENFORCE
        }
        return suspendCoroutine { continuation ->
            provider.apply(request) { outcome -> continuation.resume(outcome) }
        }
    }

    public suspend fun clear(): IosEnforcementOutcome {
        return suspendCoroutine { continuation ->
            provider.clear { outcome -> continuation.resume(outcome) }
        }
    }
}
