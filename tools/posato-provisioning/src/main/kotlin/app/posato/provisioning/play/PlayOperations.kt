package app.posato.provisioning.play

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val UNCHANGED = "unchanged"
private const val REPLACED = "replaced"

/** The two tracks Posato releases through: internal testing first, then production after the maintainer's go. */
enum class PlayTrackName(
    val option: String,
) {
    INTERNAL("internal"),
    PRODUCTION("production"),
    ;

    companion object {
        fun of(option: String): PlayTrackName = entries.first { entry -> entry.option == option }
    }
}

/**
 * `completed` rolls the release out to everyone on the track. `draft` only stages it; Play requires it while the app
 * itself is still a draft that has never been reviewed.
 */
enum class ReleaseStatus(
    val option: String,
) {
    COMPLETED("completed"),
    DRAFT("draft"),
    ;

    companion object {
        fun of(option: String): ReleaseStatus = entries.first { entry -> entry.option == option }
    }
}

class UploadRequest(
    val bundle: ByteArray,
    val mapping: ByteArray,
    val track: PlayTrackName,
    val status: ReleaseStatus,
    val notes: List<LocalizedText>,
)

/**
 * Uploads one bundle with its R8 mapping and makes it the track's release, in one edit.
 *
 * The mapping is attached to the version code Play read from the uploaded bundle, never to one derived locally, so a
 * bundle built with an unexpected version code still gets its own mapping.
 */
class PlayUpload(
    private val client: PlayClient,
) {
    fun upload(request: UploadRequest): JsonObject = client.committing { edit ->
        val bundle = client.uploadBundle(edit, request.bundle)
        client.uploadMapping(edit, bundle.versionCode, request.mapping)
        val release = PlayRelease(
            versionCodes = listOf(bundle.versionCode.toString()),
            releaseNotes = request.notes,
            status = request.status.option,
        )
        client.updateTrack(edit, PlayTrack(request.track.option, listOf(release)))
        buildJsonObject {
            put("package", PLAY_PACKAGE)
            put("versionCode", bundle.versionCode)
            put("sha256", bundle.sha256)
            put("track", request.track.option)
            put("status", request.status.option)
            putJsonArray("releaseNoteLanguages") { request.notes.forEach { note -> add(note.language) } }
        }
    }
}

/** Replaces the listing text and the given images of every locale in one edit, so a failure changes nothing. */
class PlayListingUpdate(
    private val client: PlayClient,
) {
    fun update(listings: List<LocaleListing>): JsonObject = client.committing { edit ->
        val reports = listings.map { listing ->
            client.updateListing(
                edit,
                PlayListingText(listing.language, listing.title, listing.shortDescription, listing.fullDescription),
            )
            listing.icon?.let { image -> client.replaceImages(edit, listing.language, PlayImageType.ICON, listOf(image.bytes)) }
            listing.featureGraphic?.let { image ->
                client.replaceImages(edit, listing.language, PlayImageType.FEATURE_GRAPHIC, listOf(image.bytes))
            }
            if (listing.phoneScreenshots.isNotEmpty()) {
                client.replaceImages(edit, listing.language, PlayImageType.PHONE_SCREENSHOTS, listing.phoneScreenshots.map { it.bytes })
            }
            listing
        }
        buildJsonObject {
            put("package", PLAY_PACKAGE)
            putJsonArray("locales") {
                reports.forEach { listing ->
                    addJsonObject {
                        put("language", listing.language)
                        put("text", REPLACED)
                        put("icon", if (listing.icon == null) UNCHANGED else REPLACED)
                        put("featureGraphic", if (listing.featureGraphic == null) UNCHANGED else REPLACED)
                        put("phoneScreenshots", listing.phoneScreenshots.size)
                    }
                }
            }
        }
    }
}

/**
 * Makes a release already on one track the release of another, keeping its name and release notes, in one edit.
 * Nothing is uploaded again, so production receives exactly the bundle that was tested.
 */
class PlayPromotion(
    private val client: PlayClient,
) {
    fun promote(
        versionCode: Long,
        from: PlayTrackName,
        to: PlayTrackName,
        status: ReleaseStatus,
    ): JsonObject = client.committing { edit ->
        val code = versionCode.toString()
        val source = client.tracks(edit)
            .firstOrNull { track -> track.track == from.option }
            ?.releases
            ?.firstOrNull { release -> code in release.versionCodes }
            ?: throw ProvisioningException(
                ErrorCode.PLAY_RELEASE_MISSING,
                "The ${from.option} track holds no release with version code $versionCode.",
                "Run `play status` for the version codes each track holds; upload the bundle with `play upload` first.",
            )
        val release = PlayRelease(name = source.name, versionCodes = listOf(code), releaseNotes = source.releaseNotes, status = status.option)
        client.updateTrack(edit, PlayTrack(to.option, listOf(release)))
        buildJsonObject {
            put("package", PLAY_PACKAGE)
            put("versionCode", versionCode)
            put("from", from.option)
            put("to", to.option)
            put("status", status.option)
        }
    }
}

/** Reads every track's releases and the listing locales from an edit that is then deleted, never committed. */
class PlayStatusReport(
    private val client: PlayClient,
) {
    fun report(): JsonObject = client.discarding { edit ->
        val tracks = client.tracks(edit)
        val listings = client.listings(edit)
        buildJsonObject {
            put("package", PLAY_PACKAGE)
            putJsonArray("tracks") {
                tracks.forEach { track ->
                    addJsonObject {
                        put("track", track.track)
                        putJsonArray("releases") { track.releases.forEach { release -> add(describe(release)) } }
                    }
                }
            }
            putJsonArray("listingLocales") { listings.map { it.language }.sorted().forEach { language -> add(language) } }
        }
    }

    private fun describe(release: PlayRelease): JsonObject = buildJsonObject {
        put("name", release.name)
        putJsonArray("versionCodes") { release.versionCodes.forEach { code -> add(code) } }
        put("status", release.status)
        put("userFraction", release.userFraction)
        putJsonArray("releaseNoteLanguages") { release.releaseNotes.forEach { note -> add(note.language) } }
    }
}
