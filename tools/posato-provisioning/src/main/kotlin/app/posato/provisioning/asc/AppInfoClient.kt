package app.posato.provisioning.asc

import app.posato.provisioning.model.AppInfoLocalizationResource
import app.posato.provisioning.model.AppInfoResource
import app.posato.provisioning.model.RelationshipRef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val APP_NAME = "Posato"

/**
 * The app information operations a release needs: the app's App Information records and their localizations, which
 * hold the subtitle. App Store Connect gives each App Store version being prepared its own editable copy.
 */
class AppInfoClient(
    executor: AscRequestExecutor
) {
    private val documents = AscDocuments(executor)

    fun appInfos(appId: String): List<AppInfoResource> = documents.list(
        "apps/$appId/appInfos",
        emptyList(),
        AppInfoResource.serializer(),
        STORE_LISTING_HINT,
    )

    fun localizations(appInfoId: String): List<AppInfoLocalizationResource> = documents.list(
        "appInfos/$appInfoId/appInfoLocalizations",
        emptyList(),
        AppInfoLocalizationResource.serializer(),
        STORE_LISTING_HINT,
    )

    /**
     * Adds one App Information localization. The name stays Posato in every language; the subtitle is the localized line.
     */
    fun createLocalization(
        appInfoId: String,
        locale: String,
        subtitle: String,
    ): AppInfoLocalizationResource = documents.write(
        HttpMethod.POST,
        "appInfoLocalizations",
        JsonApi.resource(
            type = "appInfoLocalizations",
            attributes = buildJsonObject {
                put("locale", locale)
                put("name", APP_NAME)
                put("subtitle", subtitle)
            },
            relationships = mapOf("appInfo" to RelationshipRef(appInfoId, "appInfos")),
        ),
        AppInfoLocalizationResource.serializer(),
    )

    /** Writes only the given localization attributes, such as `subtitle`. */
    fun updateLocalization(
        localizationId: String,
        attributes: Map<String, String>,
    ): AppInfoLocalizationResource = documents.write(
        HttpMethod.PATCH,
        "appInfoLocalizations/$localizationId",
        JsonApi.resource(
            type = "appInfoLocalizations",
            id = localizationId,
            attributes = buildJsonObject { attributes.forEach { (key, value) -> put(key, value) } },
        ),
        AppInfoLocalizationResource.serializer(),
    )
}
