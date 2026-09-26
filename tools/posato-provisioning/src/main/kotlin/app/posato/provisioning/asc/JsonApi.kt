package app.posato.provisioning.asc

import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.RelationshipRef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The request documents the store operations send.
 *
 * They are built as JSON rather than one data class per request because the store operations send a dozen small
 * shapes that differ only in type, attributes, and relationships; the tests assert each shape field by field.
 */
object JsonApi {
    fun resource(
        type: String,
        id: String? = null,
        attributes: JsonObject? = null,
        relationships: Map<String, RelationshipRef> = emptyMap(),
    ): String = encode(
        buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put("type", type)
                    id?.let { put("id", it) }
                    attributes?.let { put("attributes", it) }
                    if (relationships.isNotEmpty()) {
                        put(
                            "relationships",
                            buildJsonObject { relationships.forEach { (name, ref) -> put(name, linkageObject(ref)) } },
                        )
                    }
                },
            )
        },
    )

    /** The body of a to-one relationship `PATCH`: the linkage alone. */
    fun linkage(ref: RelationshipRef): String = encode(linkageObject(ref))

    private fun linkageObject(ref: RelationshipRef): JsonObject = buildJsonObject {
        put(
            "data",
            buildJsonObject {
                put("type", ref.type)
                put("id", ref.id)
            },
        )
    }

    private fun encode(json: JsonObject): String = ProvisioningJson.compact.encodeToString(JsonObject.serializer(), json)
}
