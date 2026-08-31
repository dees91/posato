package app.posato.feature.targets.data

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

public class LocalApplicationMapping private constructor(
    public val id: LocalApplicationMappingId,
    public val displayName: String,
) {
    override fun equals(other: Any?): Boolean {
        return other is LocalApplicationMapping && id == other.id && displayName == other.displayName
    }

    override fun hashCode(): Int {
        return 31 * id.hashCode() + displayName.hashCode()
    }

    override fun toString(): String {
        return "LocalApplicationMapping(redacted)"
    }

    public companion object {
        public fun restore(
            id: LocalApplicationMappingId,
            displayName: String,
        ): LocalApplicationMapping? {
            val encodedName = try {
                displayName.encodeToByteArray(throwOnInvalidSequence = true)
            } catch (_: Exception) {
                return null
            }
            val isValid = displayName.isNotEmpty() &&
                displayName == displayName.trim() &&
                displayName.none { character -> character.isApplicationMappingControlCharacter() } &&
                encodedName.size <= LocalApplicationMappingLimits.MAXIMUM_DISPLAY_NAME_BYTES

            return if (isValid) LocalApplicationMapping(id, displayName) else null
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
                .sortedWith(
                    compareBy<LocalApplicationMapping> { mapping -> mapping.displayName.lowercase() }
                        .thenBy { mapping -> mapping.id.canonicalValue },
                )
            val hasValidCount = restored.size <= LocalApplicationMappingLimits.MAXIMUM_MAPPINGS
            val hasUniqueIdentifiers = restored.map { mapping -> mapping.id }.toSet().size == restored.size

            return if (hasValidCount && hasUniqueIdentifiers) LocalApplicationMappingsSnapshot(restored) else null
        }
    }
}

public enum class LocalApplicationMappingsLoadFailure { STORAGE, CORRUPTION }

public sealed interface LocalApplicationMappingsLoadResult {
    public data class Success(
        public val snapshot: LocalApplicationMappingsSnapshot,
    ) : LocalApplicationMappingsLoadResult {
        override fun toString(): String {
            return "LocalApplicationMappingsLoadResult.Success(redacted)"
        }
    }

    public data object Unavailable : LocalApplicationMappingsLoadResult

    public data class Failure(
        public val reason: LocalApplicationMappingsLoadFailure,
    ) : LocalApplicationMappingsLoadResult
}

public enum class LocalApplicationSelectionRejection { SELF, INVALID_OR_UNSIGNED, CAPACITY }

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
    public suspend fun load(): LocalApplicationMappingsLoadResult

    public suspend fun chooseApplications(): LocalApplicationSelectionResult

    public suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult
}

internal object UnavailableLocalApplicationMappings : LocalApplicationMappings {
    override suspend fun load(): LocalApplicationMappingsLoadResult {
        return LocalApplicationMappingsLoadResult.Unavailable
    }

    override suspend fun chooseApplications(): LocalApplicationSelectionResult {
        return LocalApplicationSelectionResult.Unavailable
    }

    override suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult {
        return LocalApplicationRemovalResult.Unavailable
    }
}

private fun Char.isApplicationMappingControlCharacter(): Boolean {
    return this in '\u0000'..'\u001F' || this in '\u007F'..'\u009F'
}
