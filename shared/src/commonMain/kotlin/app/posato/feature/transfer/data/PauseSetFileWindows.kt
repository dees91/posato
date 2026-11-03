package app.posato.feature.transfer.data

import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.WebsiteWindow
import app.posato.feature.transfer.domain.FileSet
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A set's rules as website to period and length, in the set's website order (product rules, Export and import). */
internal fun windowsJson(set: FileSet): JsonObject {
    return buildJsonObject {
        set.domains.forEach { domain ->
            set.windows[domain]?.let { window ->
                put(
                    domain.canonicalValue,
                    buildJsonObject {
                        put("period", window.periodMinutes)
                        put("length", window.lengthMinutes)
                    },
                )
            }
        }
    }
}

/**
 * The optional rules field. An entry outside the fixed choices or for a website not in the set is dropped and that
 * website imports without a rule, which only blocks more; a field of another shape drops every rule.
 */
internal fun readWindows(
    element: JsonElement?,
    domains: List<ExactDomain>,
): Map<ExactDomain, WebsiteWindow> {
    val raw = element as? JsonObject ?: return emptyMap()
    return raw.entries.mapNotNull { (key, value) ->
        val domain = ExactDomain.restore(key)?.takeIf { it in domains && it.canonicalValue == key }
        val rule = (value as? JsonObject)?.let { pair -> WebsiteWindow.of(pair.number("period") ?: -1, pair.number("length") ?: -1) }
        if (domain != null && rule != null) domain to rule else null
    }.toMap()
}

private fun JsonObject.number(key: String): Int? {
    return (this[key] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.content?.toIntOrNull()
}
