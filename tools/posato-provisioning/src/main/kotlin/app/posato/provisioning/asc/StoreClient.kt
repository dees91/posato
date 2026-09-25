package app.posato.provisioning.asc

import app.posato.provisioning.model.AppResource
import app.posato.provisioning.model.AppStoreVersionResource
import app.posato.provisioning.model.BuildResource
import app.posato.provisioning.model.LocalizationResource
import app.posato.provisioning.model.PreReleaseVersionResource
import app.posato.provisioning.model.RelationshipRef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val PLATFORM = "IOS"
private const val PRE_RELEASE_VERSIONS = "preReleaseVersions"

/** A build with the marketing version App Store Connect files it under, which the build resource alone does not carry. */
data class StoreBuild(
    val id: String,
    val number: String?,
    val version: String?,
    val processingState: String?,
    val uploadedDate: String?,
    val valid: Boolean,
)

data class BuildListing(
    val builds: List<StoreBuild>,
    val hasMore: Boolean,
)

/**
 * The App Store version operations an iOS release needs: the app record, its versions, its builds, and the version's
 * localizations. Screenshots and review submissions live in [ScreenshotClient] and [ReviewClient].
 */
class StoreClient(
    executor: AscRequestExecutor
) {
    private val documents = AscDocuments(executor)

    /**
     * The app record for one bundle identifier.
     *
     * The match is exact and made here, because `filter[bundleId]` is a contains match: `app.posato.ios` would also
     * select a record for any identifier that merely contains it.
     */
    fun app(bundleId: String): AppResource? = documents
        .list("apps", listOf("filter[bundleId]" to bundleId), AppResource.serializer(), STORE_LISTING_HINT)
        .firstOrNull { app -> app.attributes.bundleId == bundleId }

    fun versions(appId: String): List<AppStoreVersionResource> = documents.list(
        "apps/$appId/appStoreVersions",
        listOf("filter[platform]" to PLATFORM),
        AppStoreVersionResource.serializer(),
        STORE_LISTING_HINT,
    )

    fun createVersion(
        appId: String,
        versionString: String,
        releaseType: String,
    ): AppStoreVersionResource = documents.write(
        HttpMethod.POST,
        "appStoreVersions",
        JsonApi.resource(
            type = "appStoreVersions",
            attributes = buildJsonObject {
                put("platform", PLATFORM)
                put("versionString", versionString)
                put("releaseType", releaseType)
            },
            relationships = mapOf("app" to RelationshipRef(appId, "apps")),
        ),
        AppStoreVersionResource.serializer(),
    )

    fun updateReleaseType(
        versionId: String,
        releaseType: String,
    ): AppStoreVersionResource = documents.write(
        HttpMethod.PATCH,
        "appStoreVersions/$versionId",
        JsonApi.resource(type = "appStoreVersions", id = versionId, attributes = buildJsonObject { put("releaseType", releaseType) }),
        AppStoreVersionResource.serializer(),
    )

    /** The newest builds first. A second page is reported rather than followed; the newest page is what a release reads. */
    fun builds(appId: String): BuildListing = builds(listOf("filter[app]" to appId, "sort" to "-uploadedDate"))

    /** The builds carrying one build number, across every marketing version. */
    fun builds(
        appId: String,
        number: Int,
    ): BuildListing = builds(listOf("filter[app]" to appId, "filter[version]" to number.toString()))

    fun attachedBuild(versionId: String): BuildResource? = documents.optional("appStoreVersions/$versionId/build", BuildResource.serializer())

    fun attachBuild(
        versionId: String,
        buildId: String,
    ) = documents.send(
        HttpMethod.PATCH,
        "appStoreVersions/$versionId/relationships/build",
        JsonApi.linkage(RelationshipRef(buildId, "builds")),
    )

    fun localizations(versionId: String): List<LocalizationResource> = documents.list(
        "appStoreVersions/$versionId/appStoreVersionLocalizations",
        emptyList(),
        LocalizationResource.serializer(),
        STORE_LISTING_HINT,
    )

    fun updateWhatsNew(
        localizationId: String,
        whatsNew: String,
    ): LocalizationResource = documents.write(
        HttpMethod.PATCH,
        "appStoreVersionLocalizations/$localizationId",
        JsonApi.resource(
            type = "appStoreVersionLocalizations",
            id = localizationId,
            attributes = buildJsonObject { put("whatsNew", whatsNew) },
        ),
        LocalizationResource.serializer(),
    )

    private fun builds(filters: List<Pair<String, String>>): BuildListing {
        val page = documents.firstPage("builds", filters + ("include" to "preReleaseVersion"), BuildResource.serializer())
        val versions = page.included
            .filter { element -> element["type"]?.jsonPrimitive?.content == PRE_RELEASE_VERSIONS }
            .map { element -> documents.included("builds", element, PreReleaseVersionResource.serializer()) }
            .associate { resource -> resource.id to resource.attributes.version }
        val builds = page.data.map { build ->
            StoreBuild(
                id = build.id,
                number = build.attributes.version,
                version = build.relationships.preReleaseVersion.data?.id?.let(versions::get),
                processingState = build.attributes.processingState,
                uploadedDate = build.attributes.uploadedDate,
                valid = build.valid,
            )
        }
        return BuildListing(builds, page.hasMore)
    }
}
