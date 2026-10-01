package app.posato.desktop.mappings

import app.posato.feature.targets.data.LocalApplicationMappingId

/**
 * Resolves the apps a pause holds to their designated requirements: from the app choices, or, for an app a
 * running part keeps after it left every set, from what that part kept. An app found in neither fails the
 * resolution, as an unknown app always has.
 */
internal class ApplicationRequirements(
    private val mappings: DesktopLocalApplicationMappings,
    private val retained: suspend (Set<String>) -> Map<String, ByteArray>,
) {
    suspend fun resolve(ids: List<LocalApplicationMappingId>): List<ByteArray> {
        val stored = mappings.storedRequirements()
        val missing = ids.map(LocalApplicationMappingId::canonicalValue).filterNot(stored::containsKey).toSet()
        val kept = if (missing.isEmpty()) emptyMap() else retained(missing)
        return ids.map { id ->
            stored[id.canonicalValue] ?: kept[id.canonicalValue] ?: throw IllegalArgumentException("Unknown application mapping")
        }
    }
}
