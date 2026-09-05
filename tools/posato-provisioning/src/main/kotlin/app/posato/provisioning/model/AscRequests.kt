package app.posato.provisioning.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateDeviceAttributes(
    val name: String,
    val platform: String,
    val udid: String,
)

@Serializable
data class CreateDeviceData(
    val attributes: CreateDeviceAttributes,
    val type: String = "devices",
)

@Serializable
data class CreateDeviceRequest(
    val data: CreateDeviceData,
)

@Serializable
data class CreateCertificateAttributes(
    val csrContent: String,
    val certificateType: String = "DEVELOPMENT",
)

@Serializable
data class CreateCertificateData(
    val attributes: CreateCertificateAttributes,
    val type: String = "certificates",
)

@Serializable
data class CreateCertificateRequest(
    val data: CreateCertificateData,
)

@Serializable
data class CreateProfileAttributes(
    val name: String,
    val profileType: String,
)

@Serializable
data class CreateProfileRelationships(
    val bundleId: ToOne,
    val certificates: ToMany,
    val devices: ToMany,
)

@Serializable
data class CreateProfileData(
    val attributes: CreateProfileAttributes,
    val relationships: CreateProfileRelationships,
    val type: String = "profiles",
)

@Serializable
data class CreateProfileRequest(
    val data: CreateProfileData,
)
