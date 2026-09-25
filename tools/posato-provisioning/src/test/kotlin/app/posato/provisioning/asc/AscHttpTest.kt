package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Transcript
import java.io.IOException
import java.net.URI
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FROZEN: Instant = Instant.parse("2026-09-05T12:00:00Z")
private const val BODY_CAP = 4 * 1024 * 1024

class AscHttpTest {
    @Test
    fun `retries a rate limited GET and returns the eventual success`() {
        val exchange = ScriptedExchange(listOf(Reply(429), Reply(503), Reply(200, "{}")))
        val slept = mutableListOf<Duration>()

        val response = httpWith(exchange, slept).execute(AscRequest(HttpMethod.GET, "bundleIds"))

        assertEquals(200, response.status)
        assertEquals(3, exchange.sent.size)
        assertEquals(listOf(Duration.ofSeconds(1), Duration.ofSeconds(2)), slept)
    }

    @Test
    fun `attempts a POST exactly once even when it times out`() {
        val exchange = ScriptedExchange(listOf(Reply(failure = HttpTimeoutException("timed out")), Reply(200, "{}")))

        val failure = assertFailsWith<ProvisioningException> {
            httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.POST, "devices", body = "{}"))
        }

        assertEquals(ErrorCode.ASC_UNREACHABLE, failure.code)
        assertEquals(1, exchange.sent.size)
    }

    @Test
    fun `attempts a PATCH exactly once even when the service is unavailable`() {
        val exchange = ScriptedExchange(listOf(Reply(503), Reply(200, "{}")))

        val failure = assertFailsWith<ProvisioningException> {
            httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.PATCH, "appStoreVersions/VER", body = "{}"))
        }

        assertEquals(ErrorCode.ASC_UNAVAILABLE, failure.code)
        assertEquals(1, exchange.sent.size)
    }

    @Test
    fun `attempts a DELETE exactly once even when the service is unavailable`() {
        val exchange = ScriptedExchange(listOf(Reply(503), Reply(200, "{}")))

        val failure = assertFailsWith<ProvisioningException> {
            httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.DELETE, "profiles"))
        }

        assertEquals(ErrorCode.ASC_UNAVAILABLE, failure.code)
        assertEquals(1, exchange.sent.size)
    }

    @Test
    fun `never retries an unauthorized response`() {
        val exchange = ScriptedExchange(listOf(Reply(401), Reply(200, "{}")))

        val failure = assertFailsWith<ProvisioningException> {
            httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.GET, "bundleIds"))
        }

        assertEquals(ErrorCode.ASC_UNAUTHORIZED, failure.code)
        assertEquals(1, exchange.sent.size)
    }

    @Test
    fun `gives up once the deadline is spent rather than retrying forever`() {
        val exchange = ScriptedExchange(listOf(Reply(503), Reply(503), Reply(200, "{}")))
        val clock = MutableClock(FROZEN)
        val sleeper = Sleeper { clock.now = clock.now.plusSeconds(120) }
        val source = TokenSource("KEYID12345", "issuer", TestKeys.generate().private, clock)

        val failure = assertFailsWith<ProvisioningException> {
            AscHttp(source, Transcript { }, clock, sleeper, exchange).execute(AscRequest(HttpMethod.GET, "bundleIds"))
        }

        assertEquals(ErrorCode.ASC_UNAVAILABLE, failure.code)
        assertEquals(1, exchange.sent.size)
    }

    @Test
    fun `rejects a response larger than the cap`() {
        val exchange = ScriptedExchange(listOf(Reply(200, "x".repeat(BODY_CAP + 1))))

        val failure = assertFailsWith<ProvisioningException> {
            httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.GET, "profiles"))
        }

        assertEquals(ErrorCode.ASC_RESPONSE_TOO_LARGE, failure.code)
    }

    @Test
    fun `builds a pinned https uri with an encoded query`() {
        val exchange = ScriptedExchange(listOf(Reply(200, "{}")))

        httpWith(exchange, mutableListOf()).execute(
            AscRequest(HttpMethod.GET, "certificates", listOf("filter[certificateType]" to "DEVELOPMENT", "limit" to "200")),
        )

        val uri = exchange.sent.single()
        assertEquals("https", uri.scheme)
        assertEquals("api.appstoreconnect.apple.com", uri.host)
        assertEquals("/v1/certificates", uri.path)
        assertEquals("filter%5BcertificateType%5D=DEVELOPMENT&limit=200", uri.rawQuery)
    }

    @Test
    fun `refuses a path that is not a plain resource path`() {
        val exchange = ScriptedExchange(listOf(Reply(200, "{}")))

        val failure = assertFailsWith<ProvisioningException> {
            httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.GET, "https://evil.example.com/v1/apps"))
        }

        assertEquals(ErrorCode.COMMAND_FAILED, failure.code)
        assertTrue(exchange.sent.isEmpty())
    }

    @Test
    fun `carries only the enumerated error codes into the failure message`() {
        val body = """{"errors":[{"code":"ENTITY_ERROR.ATTRIBUTE.INVALID","title":"UDID SECRETVALUE is invalid","detail":"SECRETVALUE"}]}"""
        val exchange = ScriptedExchange(listOf(Reply(422, body)))

        val failure = assertFailsWith<ProvisioningException> {
            httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.POST, "devices", body = "{}"))
        }

        assertEquals(ErrorCode.ASC_REJECTED, failure.code)
        assertTrue(failure.message.orEmpty().contains("ENTITY_ERROR.ATTRIBUTE.INVALID"))
        assertFalse(failure.message.orEmpty().contains("SECRETVALUE"))
    }

    @Test
    fun `gives the exchange the remaining deadline and the size bound`() {
        // The bound has to reach the transport: a body read outside the deadline would hang the command past the
        // limit the tool promises, however carefully the retry loop accounts for time.
        val exchange = ScriptedExchange(listOf(Reply(200, "{}")))

        httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.GET, "bundleIds"))

        assertEquals(BODY_CAP, exchange.caps.single())
        assertTrue(exchange.timeouts.single() <= Duration.ofSeconds(60))
        assertTrue(exchange.timeouts.single() > Duration.ZERO)
    }

    @Test
    fun `sends the signed token as a bearer credential`() {
        val exchange = ScriptedExchange(listOf(Reply(200, "{}")))

        httpWith(exchange, mutableListOf()).execute(AscRequest(HttpMethod.GET, "bundleIds"))

        assertEquals(3, exchange.tokens.single().split(".").size)
    }

    private fun httpWith(
        exchange: HttpExchange,
        slept: MutableList<Duration>
    ): AscHttp {
        val clock = MutableClock(FROZEN)
        val sleeper = Sleeper { duration ->
            slept.add(duration)
            clock.now = clock.now.plus(duration)
        }
        val source = TokenSource("KEYID12345", "issuer", TestKeys.generate().private, clock)
        return AscHttp(source, Transcript { }, clock, sleeper, exchange)
    }

    private data class Reply(
        val status: Int = 200,
        val body: String = "",
        val failure: IOException? = null,
    )

    private class ScriptedExchange(
        private val replies: List<Reply>
    ) : HttpExchange {
        val sent = mutableListOf<URI>()
        val tokens = mutableListOf<String>()
        val caps = mutableListOf<Int>()
        val timeouts = mutableListOf<Duration>()

        override fun send(
            uri: URI,
            method: HttpMethod,
            body: String?,
            token: String,
            timeout: Duration,
            maxBytes: Int,
        ): RawResponse {
            val reply = replies[sent.size]
            sent.add(uri)
            tokens.add(token)
            caps.add(maxBytes)
            timeouts.add(timeout)
            reply.failure?.let { throw it }
            return RawResponse(reply.status, reply.body.toByteArray().copyOf(minOf(reply.body.length, maxBytes + 1)))
        }
    }
}
