package app.posato.feature.targets.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.jvm.JvmInline

public object LocalApplicationMappingLimits {
    public const val MAXIMUM_MAPPINGS: Int = 64
    public const val MAXIMUM_DISPLAY_NAME_BYTES: Int = 256
    public const val MAPPING_ID_HEX_LENGTH: Int = 64
}

@JvmInline
public value class LocalApplicationMappingId private constructor(
    public val canonicalValue: String,
) {
    override fun toString(): String {
        return "LocalApplicationMappingId(redacted)"
    }

    public companion object {
        public fun restore(canonicalValue: String): LocalApplicationMappingId? {
            val isCanonical = canonicalValue.length == LocalApplicationMappingLimits.MAPPING_ID_HEX_LENGTH &&
                canonicalValue.all { character -> character in '0'..'9' || character in 'a'..'f' }

            return if (isCanonical) LocalApplicationMappingId(canonicalValue) else null
        }
    }
}

public sealed interface LocalApplicationMappingDisplay {
    public class Named private constructor(
        public val value: String,
    ) : LocalApplicationMappingDisplay {
        override fun equals(other: Any?): Boolean {
            return other is Named && value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun toString(): String {
            return "LocalApplicationMappingDisplay.Named(redacted)"
        }

        public companion object {
            public fun restore(value: String): Named? {
                val encodedName = try {
                    value.encodeToByteArray(throwOnInvalidSequence = true)
                } catch (_: Exception) {
                    return null
                }
                val isValid = value.isNotEmpty() &&
                    value == value.trim() &&
                    value.none { character -> character.isApplicationMappingControlCharacter() } &&
                    encodedName.size <= LocalApplicationMappingLimits.MAXIMUM_DISPLAY_NAME_BYTES

                return if (isValid) Named(value) else null
            }
        }
    }

    public object Opaque : LocalApplicationMappingDisplay {
        override fun toString(): String {
            return "LocalApplicationMappingDisplay.Opaque(redacted)"
        }
    }
}

public class LocalApplicationMapping private constructor(
    public val id: LocalApplicationMappingId,
    public val display: LocalApplicationMappingDisplay,
) {
    override fun equals(other: Any?): Boolean {
        return other is LocalApplicationMapping && id == other.id && display == other.display
    }

    override fun hashCode(): Int {
        return 31 * id.hashCode() + display.hashCode()
    }

    override fun toString(): String {
        return "LocalApplicationMapping(redacted)"
    }

    public companion object {
        public fun restore(
            id: LocalApplicationMappingId,
            displayName: String,
        ): LocalApplicationMapping? {
            val display = LocalApplicationMappingDisplay.Named.restore(displayName) ?: return null

            return LocalApplicationMapping(id, display)
        }

        public fun restoreOpaque(id: LocalApplicationMappingId): LocalApplicationMapping {
            return LocalApplicationMapping(id, LocalApplicationMappingDisplay.Opaque)
        }
    }
}

public class LocalApplicationMappingsSnapshot private constructor(
    public val mappings: List<LocalApplicationMapping>,
) {
    override fun equals(other: Any?): Boolean {
        return other is LocalApplicationMappingsSnapshot && mappings == other.mappings
    }

    override fun hashCode(): Int {
        return mappings.hashCode()
    }

    override fun toString(): String {
        return "LocalApplicationMappingsSnapshot(redacted)"
    }

    public companion object {
        public fun empty(): LocalApplicationMappingsSnapshot {
            return LocalApplicationMappingsSnapshot(emptyList())
        }

        public fun restore(mappings: Iterable<LocalApplicationMapping>): LocalApplicationMappingsSnapshot? {
            val restored = mappings
                .toList()
                .sortedWith(applicationMappingComparator)
            val hasValidCount = restored.size <= LocalApplicationMappingLimits.MAXIMUM_MAPPINGS
            val hasUniqueIdentifiers = restored.map { mapping -> mapping.id }.toSet().size == restored.size

            return if (hasValidCount && hasUniqueIdentifiers) LocalApplicationMappingsSnapshot(restored) else null
        }
    }
}

public enum class LocalApplicationMappingsLoadFailure { STORAGE, CORRUPTION }

public enum class LocalApplicationMappingsAccess { READY, AUTHORIZATION_REQUIRED, AUTHORIZATION_DENIED, RESTRICTED }

public sealed interface LocalApplicationMappingsLoadResult {
    public data class Success(
        public val snapshot: LocalApplicationMappingsSnapshot,
        public val access: LocalApplicationMappingsAccess = LocalApplicationMappingsAccess.READY,
    ) : LocalApplicationMappingsLoadResult {
        override fun toString(): String {
            return "LocalApplicationMappingsLoadResult.Success(redacted)"
        }
    }

    public data class Unavailable(
        public val snapshot: LocalApplicationMappingsSnapshot = LocalApplicationMappingsSnapshot.empty(),
    ) : LocalApplicationMappingsLoadResult {
        override fun toString(): String {
            return "LocalApplicationMappingsLoadResult.Unavailable(redacted)"
        }
    }

    public data class Failure(
        public val reason: LocalApplicationMappingsLoadFailure,
    ) : LocalApplicationMappingsLoadResult
}

public enum class LocalApplicationSelectionRejection { SELF, INVALID_OR_UNSIGNED, UNSUPPORTED, CAPACITY }

public enum class LocalApplicationSelectionFailure { PICKER, STORAGE }

public sealed interface LocalApplicationSelectionResult {
    public data class Success(
        public val snapshot: LocalApplicationMappingsSnapshot,
    ) : LocalApplicationSelectionResult {
        override fun toString(): String {
            return "LocalApplicationSelectionResult.Success(redacted)"
        }
    }

    public data object Cancelled : LocalApplicationSelectionResult

    public data class AccessChanged(
        public val snapshot: LocalApplicationMappingsSnapshot,
        public val access: LocalApplicationMappingsAccess,
    ) : LocalApplicationSelectionResult {
        override fun toString(): String {
            return "LocalApplicationSelectionResult.AccessChanged(redacted)"
        }
    }

    public data object Unavailable : LocalApplicationSelectionResult

    public data class Rejected(
        public val reason: LocalApplicationSelectionRejection,
    ) : LocalApplicationSelectionResult

    public data class Failure(
        public val reason: LocalApplicationSelectionFailure,
    ) : LocalApplicationSelectionResult
}

public enum class LocalApplicationRemovalFailure { STORAGE }

public sealed interface LocalApplicationRemovalResult {
    public data class Success(
        public val snapshot: LocalApplicationMappingsSnapshot,
    ) : LocalApplicationRemovalResult {
        override fun toString(): String {
            return "LocalApplicationRemovalResult.Success(redacted)"
        }
    }

    public data object Unavailable : LocalApplicationRemovalResult

    public data class Failure(
        public val reason: LocalApplicationRemovalFailure,
    ) : LocalApplicationRemovalResult
}

public interface LocalApplicationMappings {
    public val invalidations: Flow<Unit>
        get() = emptyFlow()

    public suspend fun load(): LocalApplicationMappingsLoadResult

    public suspend fun chooseApplications(): LocalApplicationSelectionResult

    public suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult

    public suspend fun clear(): LocalApplicationRemovalResult
}

internal object UnavailableLocalApplicationMappings : LocalApplicationMappings {
    override suspend fun load(): LocalApplicationMappingsLoadResult {
        return LocalApplicationMappingsLoadResult.Unavailable()
    }

    override suspend fun chooseApplications(): LocalApplicationSelectionResult {
        return LocalApplicationSelectionResult.Unavailable
    }

    override suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult {
        return LocalApplicationRemovalResult.Unavailable
    }

    override suspend fun clear(): LocalApplicationRemovalResult {
        return LocalApplicationRemovalResult.Unavailable
    }
}

private val applicationMappingComparator = Comparator<LocalApplicationMapping> { left, right ->
    val displayComparison = when {
        left.display is LocalApplicationMappingDisplay.Named && right.display is LocalApplicationMappingDisplay.Named -> {
            left.display.value.lowercase().compareTo(right.display.value.lowercase())
        }

        left.display is LocalApplicationMappingDisplay.Named -> {
            -1
        }

        right.display is LocalApplicationMappingDisplay.Named -> {
            1
        }

        else -> {
            0
        }
    }

    if (displayComparison != 0) displayComparison else left.id.canonicalValue.compareTo(right.id.canonicalValue)
}

private fun Char.isApplicationMappingControlCharacter(): Boolean {
    return this in '\u0000'..'\u001F' || this in '\u007F'..'\u009F'
}
