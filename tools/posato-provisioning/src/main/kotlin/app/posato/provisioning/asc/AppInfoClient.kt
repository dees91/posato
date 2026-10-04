package app.posato.provisioning.asc

import app.posato.provisioning.model.AppInfoLocalizationResource
import app.posato.provisioning.model.AppInfoResource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
