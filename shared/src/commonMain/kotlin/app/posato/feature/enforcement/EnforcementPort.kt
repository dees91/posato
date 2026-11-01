package app.posato.feature.enforcement

public enum class EnforcementOutcome {
    APPLIED,
    CLEARED,
    NOTHING_TO_ENFORCE,
    AUTHORIZATION_REQUIRED,
    INCOMPATIBLE,
    UNAVAILABLE,
    UNKNOWN,
    FAILED,
}

/**
 * One stretch of a website's window plan, until [untilEpochMillis] (exclusive), from the previous segment's end.
 * A rule of [periodMinutes] and [lengthMinutes] opens it at the aligned clock times; 0 and 0 keeps it blocked.
 */
public class WindowSegment(
    public val untilEpochMillis: Long,
    public val periodMinutes: Int,
    public val lengthMinutes: Int,
) {
    override fun equals(other: Any?): Boolean {
        return other is WindowSegment &&
            untilEpochMillis == other.untilEpochMillis &&
            periodMinutes == other.periodMinutes &&
            lengthMinutes == other.lengthMinutes
    }

    override fun hashCode(): Int {
        var result = untilEpochMillis.hashCode()
        result = 31 * result + periodMinutes
        result = 31 * result + lengthMinutes
        return result
    }

    override fun toString(): String {
        return "WindowSegment(redacted)"
    }
}

/**
 * When a held website may be left unblocked: ordered segments cut at every end of a running part that covers it.
 * After the last segment, and for a held website with no plan, it is blocked until the request ends.
 */
public class DomainWindowPlan(
    public val domain: String,
    public val segments: List<WindowSegment>,
) {
    override fun equals(other: Any?): Boolean {
        return other is DomainWindowPlan && domain == other.domain && segments == other.segments
    }

    override fun hashCode(): Int {
        return 31 * domain.hashCode() + segments.hashCode()
    }

    override fun toString(): String {
        return "DomainWindowPlan(redacted)"
    }
}

/**
 * What a host enforces: [domains] is every held website, and [windows] says when some of them may be left
 * unblocked (TARGETS-013). A host that does not read [windows] blocks every held website, the safe direction.
 */
public class EnforcementRequest(
    public val domains: List<String>,
    public val mappingIds: List<String>,
    public val sessionId: String,
    public val sessionStartEpochMillis: Long,
    public val sessionEndEpochMillis: Long,
    /** Applies only through the standing grant and never asks for a password; scheduled starts use it. */
    public val grantOnly: Boolean = false,
    public val windows: List<DomainWindowPlan> = emptyList(),
) {
    public fun withGrantOnly(): EnforcementRequest {
        return EnforcementRequest(domains, mappingIds, sessionId, sessionStartEpochMillis, sessionEndEpochMillis, grantOnly = true, windows)
    }

    override fun equals(other: Any?): Boolean {
        return other is EnforcementRequest &&
            grantOnly == other.grantOnly &&
            domains == other.domains &&
            mappingIds == other.mappingIds &&
            sessionId == other.sessionId &&
            sessionStartEpochMillis == other.sessionStartEpochMillis &&
            sessionEndEpochMillis == other.sessionEndEpochMillis &&
            windows == other.windows
    }

    override fun hashCode(): Int {
        var result = domains.hashCode()
        result = 31 * result + mappingIds.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + sessionStartEpochMillis.hashCode()
        result = 31 * result + sessionEndEpochMillis.hashCode()
        result = 31 * result + grantOnly.hashCode()
        result = 31 * result + windows.hashCode()
        return result
    }

    override fun toString(): String {
        return "EnforcementRequest(redacted)"
    }
}

public class EnforcementApplyReport(
    public val outcome: EnforcementOutcome,
    public val belowPlatformMinimum: Boolean,
    public val repeatsSystemPrompt: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        return other is EnforcementApplyReport &&
            outcome == other.outcome &&
            belowPlatformMinimum == other.belowPlatformMinimum &&
            repeatsSystemPrompt == other.repeatsSystemPrompt
    }

    override fun hashCode(): Int {
        var result = outcome.hashCode()
        result = 31 * result + belowPlatformMinimum.hashCode()
        result = 31 * result + repeatsSystemPrompt.hashCode()
        return result
    }

    override fun toString(): String {
        return "EnforcementApplyReport(redacted)"
    }
}

public interface EnforcementPort {
    public val reapplyRequiresPrompt: Boolean

    public suspend fun apply(request: EnforcementRequest): EnforcementApplyReport

    public suspend fun clear(): EnforcementOutcome

    public suspend fun status(): EnforcementOutcome

    /**
     * Clears what is held and applies [request] in one step, for a helper that takes a new
     * configuration only when idle; null when nothing was cleared and the earlier restrictions stay.
     */
    public suspend fun replace(request: EnforcementRequest): EnforcementApplyReport? {
        if (clear() != EnforcementOutcome.CLEARED) {
            return null
        }
        return apply(request)
    }

    public suspend fun holdsSession(sessionId: String): Boolean {
        return false
    }

    /**
     * Records [request] as what this caller's restrictions already are, after a relaunch found the helper
     * holding them; nothing is applied. A port that keeps no state ignores it.
     */
    public suspend fun adopt(request: EnforcementRequest) {
    }

    public suspend fun peekSuspendedExpiry(sessionId: String): Boolean {
        return false
    }

    public suspend fun acknowledgeSuspendedExpiry(sessionId: String): Boolean {
        return true
    }

    public suspend fun displacedSuspendedExpiry(currentSessionId: String): ExpiryDisplacement {
        return ExpiryDisplacement(ExpiryDisplacementOutcome.ABSENT, null)
    }
}
