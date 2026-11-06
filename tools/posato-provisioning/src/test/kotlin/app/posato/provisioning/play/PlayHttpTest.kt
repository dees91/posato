package app.posato.provisioning.play

import app.posato.provisioning.asc.MutableClock
import app.posato.provisioning.asc.Sleeper
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Transcript
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The resumable upload is the one place the tool sends its bearer token to a URL the service supplied, and a write
 * that timed out may already have been applied. Both are failure modes a live run would expose only as a leaked
 * token or a duplicated release step, so they are pinned here.
 */
class PlayHttpTest {
    @Test
    fun `refuses an upload session URL outside Google's API hosts before sending the bundle or the token`() {
        listOf(
            "http://androidpublisher.googleapis.com/upload/x?upload_id=U",
            "https://evil.example.com/upload/x?upload_id=U",
            "https://googleapis.com.evil.example.com/upload/x",
            "https://user@androidpublisher.googleapis.com/upload/x",
            "https://androidpublisher.googleapis.com:8443/upload/x",
            null,
        ).forEach { location ->
            val transport = FakePlayTransport { request ->
                if (request.method == WireMethod.POST) WireResponse(200, ByteArray(0), location) else ok()
            }

            val failure = assertFailsWith<ProvisioningException>(location.toString()) {
                http(transport).resumable("edits/E1/bundles", "application/octet-stream", byteArrayOf(1, 2, 3))
            }

            assertEquals(ErrorCode.PLAY_UPLOAD_REFUSED, failure.code)
            assertEquals(1, transport.requests.size, "nothing follows a refused session URL")
            assertFalse(failure.message.orEmpty().contains("upload_id"))
        }
    }

    @Test
    fun `retries a GET on an unavailable service but sends a write exactly once`() {
        val reads = FakePlayTransport { request -> if (reads(request)) status(503) else ok() }
        http(reads).json(WireMethod.GET, "edits/E1/tracks")
        assertEquals(2, reads.requests.size)

        val writes = FakePlayTransport { status(503) }
        val failure = assertFailsWith<ProvisioningException> { http(writes).json(WireMethod.POST, "edits/E1:commit") }
        assertEquals(ErrorCode.PLAY_UNAVAILABLE, failure.code)
        assertEquals(1, writes.requests.size)
    }

    @Test
    fun `gives a large upload the time a slow uplink needs while JSON calls keep their short deadline`() {
        // The 1.6.1 R8 mapping (63.6 MB) timed out over a home connection; an upload must fit a 512 kbit/s uplink.
        val mapping = ByteArray(63_601_138)
        val slowUplink = Duration.ofSeconds(mapping.size / SLOW_UPLINK_BYTES_PER_SECOND)
        val transport = FakePlayTransport { request ->
            if (request.method == WireMethod.POST && request.uri.query == "uploadType=resumable") {
                WireResponse(200, ByteArray(0), "https://androidpublisher.googleapis.com/upload/session?upload_id=U")
            } else {
                ok()
            }
        }

        http(transport).media("edits/E1/apks/3/deobfuscationFiles/proguard", "application/octet-stream", mapping)
        http(transport).resumable("edits/E1/bundles", "application/octet-stream", mapping)
        http(transport).json(WireMethod.PUT, "edits/E1/tracks/internal")

        val media = transport.requests[0]
        val session = transport.requests[1]
        val bundle = transport.requests[2]
        val track = transport.requests[3]
        assertTrue(media.timeout >= slowUplink, "mapping upload deadline ${media.timeout} is below $slowUplink")
        assertTrue(bundle.timeout >= slowUplink, "bundle upload deadline ${bundle.timeout} is below $slowUplink")
        assertTrue(session.timeout <= Duration.ofMinutes(1), "opening a session is a short call")
        assertTrue(track.timeout <= Duration.ofMinutes(1), "a JSON call keeps its short deadline")
    }

    @Test
    fun `reports Google's status and reasons but keeps the free-text message to the redacted transcript`() {
        val transcript = mutableListOf<String>()
        val transport = FakePlayTransport {
            status(
                403,
                """{"error":{"code":403,"status":"PERMISSION_DENIED","message":"publisher@posato-test lacks access",""" +
                    """"errors":[{"reason":"forbidden","message":"publisher@posato-test lacks access"}]}}""",
            )
        }

        val failure = assertFailsWith<ProvisioningException> {
            PlayHttp({ "t" }, transport, MutableClock(Instant.EPOCH), Sleeper { }, Transcript { transcript += it }).json(WireMethod.POST, "edits")
        }

        assertEquals(ErrorCode.PLAY_FORBIDDEN, failure.code)
        assertTrue(failure.message.orEmpty().contains("PERMISSION_DENIED"))
        assertTrue(failure.message.orEmpty().contains("forbidden"))
        assertFalse(failure.message.orEmpty().contains("publisher@"))
        assertTrue(transcript.any { it.contains("lacks access") })
    }

    @Test
    fun `refuses a path an identifier could turn into another resource`() {
        val transport = FakePlayTransport { ok() }

        assertFailsWith<ProvisioningException> { http(transport).json(WireMethod.GET, "edits/../../other.app/edits") }
        assertFailsWith<ProvisioningException> { http(transport).json(WireMethod.GET, "edits/E1?x=1") }
        assertTrue(transport.requests.isEmpty())
    }

    private var failedOnce = false

    private fun reads(request: WireRequest): Boolean {
        if (request.method != WireMethod.GET || failedOnce) return false
        failedOnce = true
        return true
    }

    private fun http(transport: PlayTransport): PlayHttp = PlayHttp({ "t" }, transport, MutableClock(Instant.EPOCH), Sleeper { }, Transcript { })
}

private const val SLOW_UPLINK_BYTES_PER_SECOND = 64_000L
