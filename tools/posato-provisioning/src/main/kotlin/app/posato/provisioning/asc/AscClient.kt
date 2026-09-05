package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.AppIdentifier
import app.posato.provisioning.model.ApplePlatform
import app.posato.provisioning.model.AscList
import app.posato.provisioning.model.AscSingle
import app.posato.provisioning.model.BundleIdResource
import app.posato.provisioning.model.CertificateResource
import app.posato.provisioning.model.CreateCertificateAttributes
import app.posato.provisioning.model.CreateCertificateData
import app.posato.provisioning.model.CreateCertificateRequest
import app.posato.provisioning.model.CreateDeviceAttributes
import app.posato.provisioning.model.CreateDeviceData
import app.posato.provisioning.model.CreateDeviceRequest
import app.posato.provisioning.model.CreateProfileAttributes
import app.posato.provisioning.model.CreateProfileData
import app.posato.provisioning.model.CreateProfileRelationships
import app.posato.provisioning.model.CreateProfileRequest
import app.posato.provisioning.model.DeviceResource
import app.posato.provisioning.model.ProfileResource
import app.posato.provisioning.model.RelationshipRef
import app.posato.provisioning.model.ToMany
import app.posato.provisioning.model.ToOne
import kotlinx.serialization.KSerializer

private const val PAGE_LIMIT = "200"
private val LIMIT = "limit" to PAGE_LIMIT

/**
 * The six App Store Connect operations this tool performs, named rather than generalized.
 *
 * There is no paging. The account holds five App IDs and a handful of devices, certificates, and profiles, so a
 * second page means something unexpected: the command stops instead of following a URL the service supplied, which
 * removes the need to validate one.
 */
class AscClient(
    private val executor: AscRequestExecutor
) {
    fun bundleIds(): List<BundleIdResource> = list("bundleIds", emptyList(), BundleIdResource.serializer())

    /**
     * The App ID for one Posato bundle identifier.
     *
     * The match is exact and made here rather than by `filter[identifier]`, which App Store Connect treats as a
     * contains match: `app.posato.ios` would also select `app.posato.ios.activitymonitor`, and the tool would
     * cheerfully build a profile for the wrong target.
     */
    fun bundleId(
        identifier: AppIdentifier,
        known: List<BundleIdResource> = bundleIds()
    ): BundleIdResource? = known.firstOrNull { resource -> resource.attributes.identifier == identifier.bundleId }

    fun devices(): List<DeviceResource> = list("devices", emptyList(), DeviceResource.serializer())

    fun createDevice(
        name: String,
        platform: ApplePlatform,
        udid: String
    ): DeviceResource = create(
        path = "devices",
        body = ProvisioningJson.compact.encodeToString(
            CreateDeviceRequest.serializer(),
            CreateDeviceRequest(CreateDeviceData(CreateDeviceAttributes(name, platform.ascName, udid))),
        ),
        serializer = DeviceResource.serializer(),
    )

    fun certificates(): List<CertificateResource> = list(
        "certificates",
        listOf("filter[certificateType]" to "DEVELOPMENT"),
        CertificateResource.serializer(),
    )

    fun createCertificate(csrContent: String): CertificateResource = create(
        path = "certificates",
        body = ProvisioningJson.compact.encodeToString(
            CreateCertificateRequest.serializer(),
            CreateCertificateRequest(CreateCertificateData(CreateCertificateAttributes(csrContent))),
        ),
        serializer = CertificateResource.serializer(),
    )

    /** `include` is what makes App Store Connect fill in the device and certificate relationships a profile carries. */
    fun profiles(): List<ProfileResource> = list(
        "profiles",
        listOf("include" to "bundleId,certificates,devices"),
        ProfileResource.serializer(),
    )

    fun createProfile(
        name: String,
        profileType: String,
        bundleIdId: String,
        certificateIds: List<String>,
        deviceIds: List<String>,
    ): ProfileResource = create(
        path = "profiles",
        body = ProvisioningJson.compact.encodeToString(
            CreateProfileRequest.serializer(),
            CreateProfileRequest(
                CreateProfileData(
                    CreateProfileAttributes(name, profileType),
                    CreateProfileRelationships(
                        bundleId = ToOne(RelationshipRef(bundleIdId, "bundleIds")),
                        certificates = ToMany(certificateIds.map { id -> RelationshipRef(id, "certificates") }),
                        devices = ToMany(deviceIds.map { id -> RelationshipRef(id, "devices") }),
                    ),
                ),
            ),
        ),
        serializer = ProfileResource.serializer(),
    )

    fun deleteProfile(id: String) {
        executor.execute(AscRequest(HttpMethod.DELETE, "profiles/$id"))
    }

    private fun <T> list(
        path: String,
        query: List<Pair<String, String>>,
        serializer: KSerializer<T>,
    ): List<T> {
        val response = executor.execute(AscRequest(HttpMethod.GET, path, query + LIMIT))
        val decoded = ProvisioningJson.lenient.decodeFromString(AscList.serializer(serializer), response.body)
        if (decoded.links.next != null) {
            throw ProvisioningException(
                ErrorCode.ASC_TOO_MANY_RESULTS,
                "The account holds more $path than this tool reads in one page.",
                "Remove resources this Mac no longer needs in the developer portal, then rerun the command.",
            )
        }
        return decoded.data
    }

    private fun <T> create(
        path: String,
        body: String,
        serializer: KSerializer<T>,
    ): T {
        val response = executor.execute(AscRequest(HttpMethod.POST, path, emptyList(), body))
        return ProvisioningJson.lenient.decodeFromString(AscSingle.serializer(serializer), response.body).data
    }
}
