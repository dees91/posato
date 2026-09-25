package app.posato.provisioning.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The states App Store Connect uses to say a profile is finished. Only these authorise a delete. */
private val DEAD_PROFILE_STATES = setOf("INVALID", "EXPIRED")

@Serializable
data class AscLinks(
    val next: String? = null,
)

/**
 * One page of a resource collection.
 *
 * `included` stays untyped: only the builds listing reads it, and every other listing that asks for related resources
 * (such as a profile's devices) must not fail because an included resource carries an attribute a store type models.
 */
@Serializable
data class AscPage<T>(
    val data: List<T> = emptyList(),
    val links: AscLinks = AscLinks(),
    val included: List<JsonObject> = emptyList(),
)

@Serializable
data class AscSingle<T>(
    val data: T,
)

/** A to-one relationship read directly, such as a version's build, whose `data` is `null` when nothing is attached. */
@Serializable
data class AscOptional<T>(
    val data: T? = null,
)

@Serializable
data class RelationshipRef(
    val id: String,
    val type: String,
)

@Serializable
data class ToOne(
    val data: RelationshipRef? = null,
)

@Serializable
data class ToMany(
    val data: List<RelationshipRef> = emptyList(),
)

@Serializable
data class BundleIdAttributes(
    val identifier: String? = null,
    val name: String? = null,
    val platform: String? = null,
)

@Serializable
data class BundleIdResource(
    val id: String,
    val attributes: BundleIdAttributes = BundleIdAttributes(),
)

@Serializable
data class DeviceAttributes(
    val udid: String? = null,
    val name: String? = null,
    val platform: String? = null,
    val status: String? = null,
    val deviceClass: String? = null,
)

@Serializable
data class DeviceResource(
    val id: String,
    val attributes: DeviceAttributes = DeviceAttributes(),
) {
    val enabled: Boolean get() = attributes.status == "ENABLED"
}

@Serializable
data class CertificateAttributes(
    val certificateType: String? = null,
    val displayName: String? = null,
    val serialNumber: String? = null,
    val expirationDate: String? = null,
    val certificateContent: String? = null,
)

@Serializable
data class CertificateResource(
    val id: String,
    val attributes: CertificateAttributes = CertificateAttributes(),
)

@Serializable
data class ProfileRelationships(
    val bundleId: ToOne = ToOne(),
    val certificates: ToMany = ToMany(),
    val devices: ToMany = ToMany(),
)

@Serializable
data class ProfileAttributes(
    val name: String? = null,
    val profileType: String? = null,
    val profileState: String? = null,
    val expirationDate: String? = null,
    val uuid: String? = null,
    val profileContent: String? = null,
)

@Serializable
data class ProfileResource(
    val id: String,
    val attributes: ProfileAttributes = ProfileAttributes(),
    val relationships: ProfileRelationships = ProfileRelationships(),
) {
    val active: Boolean get() = attributes.profileState == "ACTIVE"

    /**
     * A state App Store Connect uses to say the profile is finished.
     *
     * Only these authorise a delete. Anything else, including a state this tool has never seen and a response that
     * omits the attribute, is treated as possibly-valid, because deleting a working profile is not recoverable by
     * rerunning the command.
     */
    val dead: Boolean get() = attributes.profileState in DEAD_PROFILE_STATES
}
