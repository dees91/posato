package app.posato.provisioning.play

import app.posato.provisioning.asc.MutableClock
import app.posato.provisioning.asc.Sleeper
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Transcript
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val APP = "/androidpublisher/v3/applications/app.posato.android"
private const val UPLOAD = "/upload/androidpublisher/v3/applications/app.posato.android"
private const val EDIT = "$APP/edits/E1"
private const val SESSION = "https://androidpublisher.googleapis.com$UPLOAD/edits/E1/bundles?uploadType=resumable&upload_id=U1"

/**
 * The edit workflow's failure modes that a live run would only show by breaking a real release: an edit left open
 * after a failed step (Play then refuses the next edit until it expires, and a later commit could publish half of the
 * work), a read that commits, a deobfuscation file attached to a version code other than the one Play assigned, and a
 * promotion of a version code the source track does not hold.
 */
class PlayEditTest {
    @Test
    fun `upload sends the bundle, attaches the mapping to the version code Play returned, sets the track, and commits`() {
        val transport = FakePlayTransport(happyRoutes())

        val result = PlayUpload(client(transport)).upload(
            UploadRequest(
                bundle = byteArrayOf(0x50, 0x4B, 3, 4),
                mapping = "mapping".toByteArray(),
                track = PlayTrackName.INTERNAL,
                status = ReleaseStatus.COMPLETED,
                notes = listOf(LocalizedText("en-US", "Fixes."), LocalizedText("pl-PL", "Poprawki.")),
            ),
        )

        assertEquals(
            listOf(
                "POST $APP/edits",
                "POST $UPLOAD/edits/E1/bundles",
                "PUT ${java.net.URI(SESSION).path}",
                "POST $UPLOAD/edits/E1/apks/1060101/deobfuscationFiles/proguard",
                "PUT $EDIT/tracks/internal",
                "POST $EDIT:commit",
            ),
            transport.calls(),
        )
        val track = transport.body("PUT $EDIT/tracks/internal")
        val release = track.getValue("releases").jsonArray.single().jsonObject
        assertEquals("internal", track.text("track"))
        assertEquals("1060101", release.getValue("versionCodes").jsonArray.single().jsonPrimitive.content)
        assertEquals("completed", release.text("status"))
        assertEquals(listOf("en-US", "pl-PL"), release.getValue("releaseNotes").jsonArray.map { it.jsonObject.text("language") })
        assertEquals("1060101", result.text("versionCode"))
        assertEquals("internal", result.text("track"))
    }

    @Test
    fun `a failed step deletes the edit and never commits it`() {
        val transport = FakePlayTransport { request ->
            if (request.method == WireMethod.PUT && request.uri.path.endsWith("/tracks/internal")) {
                status(403, """{"error":{"code":403,"status":"PERMISSION_DENIED","message":"The caller lacks release rights"}}""")
            } else {
                happyRoutes()(request)
            }
        }

        val failure = assertFailsWith<ProvisioningException> {
            PlayUpload(client(transport)).upload(
                UploadRequest(byteArrayOf(0x50, 0x4B, 3, 4), "m".toByteArray(), PlayTrackName.INTERNAL, ReleaseStatus.COMPLETED, emptyList()),
            )
        }

        assertEquals(ErrorCode.PLAY_FORBIDDEN, failure.code)
        assertEquals("DELETE $EDIT", transport.calls().last())
        assertTrue(transport.calls().none { it.endsWith(":commit") })
    }

    @Test
    fun `a failed delete after a failed step still reports the step's failure`() {
        val transport = FakePlayTransport { request ->
            when {
                request.uri.path.endsWith("/bundles") -> status(400, """{"error":{"code":400,"status":"INVALID_ARGUMENT"}}""")
                request.method == WireMethod.DELETE -> status(500)
                else -> happyRoutes()(request)
            }
        }

        val failure = assertFailsWith<ProvisioningException> {
            PlayUpload(client(transport)).upload(
                UploadRequest(byteArrayOf(0x50, 0x4B, 3, 4), "m".toByteArray(), PlayTrackName.INTERNAL, ReleaseStatus.DRAFT, emptyList()),
            )
        }

        assertEquals(ErrorCode.PLAY_REJECTED, failure.code)
        assertTrue(failure.message.orEmpty().contains("INVALID_ARGUMENT"))
        assertTrue(transport.calls().contains("DELETE $EDIT"))
    }

    @Test
    fun `listing replaces text and every image type of each locale inside one committed edit`() {
        val root = Files.createTempDirectory("listing")
        val locale = root.resolve("de-DE/play").createDirectories()
        locale.resolve("title.txt").writeText("Posato")
        locale.resolve("short-description.txt").writeText("Kurz.")
        locale.resolve("full-description.txt").writeText("Lang.")
        png(locale.resolve("phone/01.png"), 1080, 1920)
        png(locale.resolve("phone/02.png"), 1080, 1920)
        val transport = FakePlayTransport(happyRoutes())

        PlayListingUpdate(client(transport)).update(PlayInputs.listings(root))

        assertEquals(
            listOf(
                "POST $APP/edits",
                "PUT $EDIT/listings/de-DE",
                "DELETE $EDIT/listings/de-DE/phoneScreenshots",
                "POST $UPLOAD/edits/E1/listings/de-DE/phoneScreenshots",
                "POST $UPLOAD/edits/E1/listings/de-DE/phoneScreenshots",
                "POST $EDIT:commit",
            ),
            transport.calls(),
        )
        val listing = transport.body("PUT $EDIT/listings/de-DE")
        assertEquals("Kurz.", listing.text("shortDescription"))
    }

    @Test
    fun `status reads tracks and listings and deletes its edit instead of committing it`() {
        val transport = FakePlayTransport { request ->
            when {
                request.uri.path == "$EDIT/tracks" -> ok(
                    """{"tracks":[{"track":"internal","releases":[{"versionCodes":["1060101"],"status":"completed"}]},{"track":"production"}]}""",
                )

                request.uri.path == "$EDIT/listings" -> ok("""{"listings":[{"language":"en-US","title":"Posato"}]}""")

                else -> happyRoutes()(request)
            }
        }

        val result = PlayStatusReport(client(transport)).report()

        assertEquals("DELETE $EDIT", transport.calls().last())
        assertTrue(transport.calls().none { it.endsWith(":commit") })
        assertEquals("en-US", result.getValue("listingLocales").jsonArray.single().jsonPrimitive.content)
        val internal = result.getValue("tracks").jsonArray.first().jsonObject
        val release = internal.getValue("releases").jsonArray.single().jsonObject
        assertEquals("1060101", release.getValue("versionCodes").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun `promote copies the release notes of the source release and refuses a version code the source track lacks`() {
        val tracks = """{"tracks":[{"track":"internal","releases":[{"name":"1.6.1","versionCodes":["1060102"],"status":"completed",""" +
            """"releaseNotes":[{"language":"en-US","text":"Fixes."}]}]}]}"""
        val transport = FakePlayTransport { request -> if (request.uri.path == "$EDIT/tracks") ok(tracks) else happyRoutes()(request) }

        PlayPromotion(client(transport)).promote(1060102, PlayTrackName.INTERNAL, PlayTrackName.PRODUCTION, ReleaseStatus.COMPLETED)

        val release = transport.body("PUT $EDIT/tracks/production").getValue("releases").jsonArray.single().jsonObject
        assertEquals("1.6.1", release.text("name"))
        assertEquals("Fixes.", release.getValue("releaseNotes").jsonArray.single().jsonObject.text("text"))
        assertEquals("POST $EDIT:commit", transport.calls().last())

        val missing = FakePlayTransport { request -> if (request.uri.path == "$EDIT/tracks") ok(tracks) else happyRoutes()(request) }
        val failure = assertFailsWith<ProvisioningException> {
            PlayPromotion(client(missing)).promote(1060101, PlayTrackName.INTERNAL, PlayTrackName.PRODUCTION, ReleaseStatus.COMPLETED)
        }
        assertEquals(ErrorCode.PLAY_RELEASE_MISSING, failure.code)
        assertEquals("DELETE $EDIT", missing.calls().last())
    }

    private fun happyRoutes(): (WireRequest) -> WireResponse = { request -> uploads(request) ?: edits(request) }

    private fun uploads(request: WireRequest): WireResponse? {
        val path = request.uri.path
        return when {
            request.method == WireMethod.POST && path.endsWith("/bundles") -> WireResponse(200, ByteArray(0), SESSION)
            request.method == WireMethod.PUT && request.uri.toString() == SESSION -> ok("""{"versionCode":1060101,"sha256":"ab12"}""")
            path.contains("/deobfuscationFiles/") -> ok("""{"deobfuscationFile":{"symbolType":"proguard"}}""")
            path.contains("$UPLOAD/edits/E1/listings/") -> ok("""{"image":{"id":"I","sha256":"cd"}}""")
            else -> null
        }
    }

    private fun edits(request: WireRequest): WireResponse {
        val path = request.uri.path
        return when {
            request.method == WireMethod.POST && path == "$APP/edits" -> ok("""{"id":"E1","expiryTimeSeconds":"1793000000"}""")
            path.endsWith(":commit") -> ok("""{"id":"E1"}""")
            request.method == WireMethod.DELETE && path.contains("/listings/") -> ok("""{"deleted":[]}""")
            request.method == WireMethod.DELETE -> status(204, "")
            request.method == WireMethod.PUT -> ok(String(request.body ?: ByteArray(0)))
            else -> error("Unexpected request ${request.method} $path")
        }
    }

    private fun client(transport: PlayTransport): PlayClient = PlayClient(
        PlayHttp({ "test-token" }, transport, MutableClock(Instant.parse("2026-11-06T12:00:00Z")), Sleeper { }, Transcript { }),
    )

    private fun FakePlayTransport.body(call: String): JsonObject {
        val request = requests.first { "${it.method} ${it.uri.path}" == call }
        return PlayJson.json.decodeFromString(JsonObject.serializer(), String(request.body!!))
    }

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
}
