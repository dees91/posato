package app.posato.feature.enforcement

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

public enum class EnforcementActionKind {
    APPLY_FAILED,
    CLEAR_FAILED,
    RESUME_REQUIRED,
}

public sealed interface EnforcementState {
    public data object Inactive : EnforcementState

    public data class Active(
        val belowPlatformMinimum: Boolean,
    ) : EnforcementState {
        override fun toString(): String {
            return "EnforcementState.Active(redacted)"
        }
    }

    public data class ActionRequired(
        val kind: EnforcementActionKind,
        val repeatsSystemPrompt: Boolean,
    ) : EnforcementState {
        override fun toString(): String {
            return "EnforcementState.ActionRequired(redacted)"
        }
    }
}

public class EnforcedSet(
    public val domains: PersistentList<String> = persistentListOf(),
    public val applicationCount: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        return other is EnforcedSet && domains == other.domains && applicationCount == other.applicationCount
    }

    override fun hashCode(): Int {
        return 31 * domains.hashCode() + applicationCount.hashCode()
    }

    override fun toString(): String {
        return "EnforcedSet(redacted)"
    }
}
