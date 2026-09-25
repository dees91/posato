package app.posato.provisioning.asc

import app.posato.provisioning.model.RelationshipRef
import app.posato.provisioning.model.ScreenshotResource
import app.posato.provisioning.model.ScreenshotSetResource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The screenshot operations of one App Store version localization.
 *
 * An upload is three steps: reserve a screenshot, send its bytes to the upload operations the reservation returns
 * (see [ScreenshotUploader]), and commit it with the file's MD5 so App Store Connect can check what arrived.
 */
class ScreenshotClient(
    executor: AscRequestExecutor
) {
    private val documents = AscDocuments(executor)

    fun sets(localizationId: String): List<ScreenshotSetResource> = documents.list(
        "appStoreVersionLocalizations/$localizationId/appScreenshotSets",
        emptyList(),
        ScreenshotSetResource.serializer(),
        STORE_LISTING_HINT,
    )

    fun createSet(
        localizationId: String,
        displayType: String,
    ): ScreenshotSetResource = documents.write(
        HttpMethod.POST,
        "appScreenshotSets",
        JsonApi.resource(
            type = "appScreenshotSets",
            attributes = buildJsonObject { put("screenshotDisplayType", displayType) },
            relationships = mapOf("appStoreVersionLocalization" to RelationshipRef(localizationId, "appStoreVersionLocalizations")),
        ),
        ScreenshotSetResource.serializer(),
    )

    fun screenshots(setId: String): List<ScreenshotResource> = documents.list(
        "appScreenshotSets/$setId/appScreenshots",
        emptyList(),
        ScreenshotResource.serializer(),
        STORE_LISTING_HINT,
    )

    fun delete(screenshotId: String) = documents.send(HttpMethod.DELETE, "appScreenshots/$screenshotId")

    fun reserve(
        setId: String,
        fileName: String,
        fileSize: Int,
    ): ScreenshotResource = documents.write(
        HttpMethod.POST,
        "appScreenshots",
        JsonApi.resource(
            type = "appScreenshots",
            attributes = buildJsonObject {
                put("fileName", fileName)
                put("fileSize", fileSize)
            },
            relationships = mapOf("appScreenshotSet" to RelationshipRef(setId, "appScreenshotSets")),
        ),
        ScreenshotResource.serializer(),
    )

    fun commit(
        screenshotId: String,
        checksum: String,
    ): ScreenshotResource = documents.write(
        HttpMethod.PATCH,
        "appScreenshots/$screenshotId",
        JsonApi.resource(
            type = "appScreenshots",
            id = screenshotId,
            attributes = buildJsonObject {
                put("uploaded", true)
                put("sourceFileChecksum", checksum)
            },
        ),
        ScreenshotResource.serializer(),
    )
}
