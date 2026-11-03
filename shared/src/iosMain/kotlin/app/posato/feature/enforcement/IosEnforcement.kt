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

/** One segment of a held website's window plan: until an instant (exclusive), with a rule or none (0, 0). */
public class IosWindowSegment(
    public val untilEpochMillis: Long,
    public val periodMinutes: Int,
    public val lengthMinutes: Int,
) {
    override fun equals(other: Any?): Boolean {
        return other is IosWindowSegment &&
            untilEpochMillis == other.untilEpochMillis &&
            periodMinutes == other.periodMinutes &&
            lengthMinutes == other.lengthMinutes
    }

    override fun hashCode(): Int {
        return (31 * untilEpochMillis.hashCode() + periodMinutes) * 31 + lengthMinutes
    }

    override fun toString(): String {
        return "IosWindowSegment(redacted)"
    }
}

/** When a held website may be left open (TARGETS-013); the monitor follows it while Posato is closed. */
public class IosWindowPlan(
    public val domain: String,
    public val segments: List<IosWindowSegment>,
) {
    override fun equals(other: Any?): Boolean {
        return other is IosWindowPlan && domain == other.domain && segments == other.segments
    }

    override fun hashCode(): Int {
        return 31 * domain.hashCode() + segments.hashCode()
    }

    override fun toString(): String {
        return "IosWindowPlan(redacted)"
    }
}

public class IosEnforcementRequest(
    public val domains: List<String>,
    public val mappingIds: List<String>,
    /** The window plans of held websites; a website without one is blocked until the session ends. */
    public val windows: List<IosWindowPlan>,
    public val sessionEndEpochMillis: Long,
) {
    public constructor(domains: List<String>, mappingIds: List<String>) : this(domains, mappingIds, emptyList(), 0L)

    override fun equals(other: Any?): Boolean {
        return other is IosEnforcementRequest &&
            domains == other.domains &&
            mappingIds == other.mappingIds &&
            windows == other.windows &&
            sessionEndEpochMillis == other.sessionEndEpochMillis
    }

    override fun hashCode(): Int {
        var result = 31 * domains.hashCode() + mappingIds.hashCode()
        result = 31 * result + windows.hashCode()
        return 31 * result + sessionEndEpochMillis.hashCode()
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

    public fun status(handler: (IosEnforcementOutcome) -> Unit)
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

    public suspend fun status(): IosEnforcementOutcome {
        return suspendCoroutine { continuation ->
            provider.status { outcome -> continuation.resume(outcome) }
        }
    }
}
