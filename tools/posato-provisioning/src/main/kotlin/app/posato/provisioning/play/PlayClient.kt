package app.posato.provisioning.play

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val OCTET_STREAM = "application/octet-stream"
private const val PNG = "image/png"

@Serializable
data class LocalizedText(
    val language: String,
    val text: String,
)

@Serializable
data class PlayRelease(
    val name: String? = null,
    val versionCodes: List<String> = emptyList(),
    val releaseNotes: List<LocalizedText> = emptyList(),
    val status: String? = null,
    val userFraction: Double? = null,
)

@Serializable
data class PlayTrack(
    val track: String,
    val releases: List<PlayRelease> = emptyList(),
)

@Serializable
data class PlayListingText(
    val language: String,
    val title: String? = null,
    val shortDescription: String? = null,
    val fullDescription: String? = null,
)

@Serializable
private class TrackList(
    val tracks: List<PlayTrack> = emptyList(),
)

@Serializable
private class ListingList(
    val listings: List<PlayListingText> = emptyList(),
)

/** The `AppImageType` values this tool replaces. */
enum class PlayImageType(
    val apiName: String,
) {
    ICON("icon"),
    FEATURE_GRAPHIC("featureGraphic"),
    PHONE_SCREENSHOTS("phoneScreenshots"),
}

class UploadedBundle(
    val versionCode: Long,
    val sha256: String?,
)

/**
 * The Android Publisher v3 `edits` resources this tool uses, each one request. Every change lives inside an edit and
 * becomes visible only when the edit is committed; see [committing] and [discarding].
 */
class PlayClient(
    private val http: PlayHttp,
) {
    fun insertEdit(): String = http.json(WireMethod.POST, "edits", JsonObject(emptyMap()))["id"]
        ?.jsonPrimitive
        ?.content
        ?.takeIf { it.isNotBlank() }
        ?: throw ProvisioningException(ErrorCode.COMMAND_FAILED, "Google Play opened an edit without an id.", "Rerun the command.")

    fun commitEdit(edit: String) {
        http.json(WireMethod.POST, "edits/$edit:commit")
    }

    fun deleteEdit(edit: String) {
        http.json(WireMethod.DELETE, "edits/$edit")
    }

    fun uploadBundle(
        edit: String,
        bytes: ByteArray,
    ): UploadedBundle {
        val bundle = http.resumable("edits/$edit/bundles", OCTET_STREAM, bytes)
        val versionCode = (bundle["versionCode"] as? JsonPrimitive)?.longOrNull ?: throw ProvisioningException(
            ErrorCode.COMMAND_FAILED,
            "Google Play accepted the bundle without reporting its version code.",
            "Rerun the command; the edit was discarded.",
        )
        return UploadedBundle(versionCode, (bundle["sha256"] as? JsonPrimitive)?.content)
    }

    fun uploadMapping(
        edit: String,
        versionCode: Long,
        bytes: ByteArray,
    ) {
        http.media("edits/$edit/apks/$versionCode/deobfuscationFiles/proguard", OCTET_STREAM, bytes)
    }

    fun tracks(edit: String): List<PlayTrack> =
        PlayJson.json.decodeFromJsonElement(TrackList.serializer(), http.json(WireMethod.GET, "edits/$edit/tracks")).tracks

    fun updateTrack(
        edit: String,
        track: PlayTrack,
    ) {
        http.json(WireMethod.PUT, "edits/$edit/tracks/${track.track}", PlayJson.json.encodeToJsonElement(PlayTrack.serializer(), track))
    }

    fun listings(edit: String): List<PlayListingText> =
        PlayJson.json.decodeFromJsonElement(ListingList.serializer(), http.json(WireMethod.GET, "edits/$edit/listings")).listings

    fun updateListing(
        edit: String,
        listing: PlayListingText,
    ) {
        val body = PlayJson.json.encodeToJsonElement(PlayListingText.serializer(), listing)
        http.json(WireMethod.PUT, "edits/$edit/listings/${listing.language}", body)
    }

    /** Deletes every image of [type] for [language], then uploads [images] in order. */
    fun replaceImages(
        edit: String,
        language: String,
        type: PlayImageType,
        images: List<ByteArray>,
    ) {
        http.json(WireMethod.DELETE, "edits/$edit/listings/$language/${type.apiName}")
        images.forEach { bytes -> http.media("edits/$edit/listings/$language/${type.apiName}", PNG, bytes) }
    }

    /** Writes one line to the redacted `--verbose` transcript. */
    fun record(line: String) = http.record(line)
}
