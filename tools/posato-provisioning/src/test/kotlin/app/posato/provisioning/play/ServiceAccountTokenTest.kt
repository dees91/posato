package app.posato.provisioning.play

import app.posato.provisioning.asc.MutableClock
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.core.Transcript
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.nio.file.Files
import java.security.Signature
import java.time.Instant
import java.util.Base64
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FROZEN: Instant = Instant.parse("2026-11-06T12:00:00Z")

/**
 * Failure modes no live run can expose here, because no Play credential exists for this tool's tests: a claim set
 * Google rejects (wrong audience, scope, or a lifetime over one hour), a signature that does not verify, a token request
 * sent to the URL the downloaded key file names instead of Google's, a key file other users can read, and a key or its
 * path leaking into a failure message.
 */
class ServiceAccountTokenTest {
    @Test
    fun `signs a claim set Google accepts that the public key verifies`() {
        val pair = PlayTestKeys.generate()
        val key = ServiceAccountKey.read(PlayTestKeys.keyFile(pair))

        val assertion = GoogleJwt.assertion(key, MutableClock(FROZEN))
        val segments = assertion.split(".")

        val header = decode(segments[0])
        assertEquals("RS256", header.text("alg"))
        assertEquals("JWT", header.text("typ"))
        val claims = decode(segments[1])
        assertEquals("publisher@posato-test.iam.gserviceaccount.com", claims.text("iss"))
        assertEquals("https://www.googleapis.com/auth/androidpublisher", claims.text("scope"))
        assertEquals("https://oauth2.googleapis.com/token", claims.text("aud"))
        assertEquals(FROZEN.epochSecond, claims.text("iat").toLong())
        val lifetime = claims.text("exp").toLong() - claims.text("iat").toLong()
        assertTrue(lifetime in 1..3600, "lifetime was $lifetime s")
        assertFalse(claims.containsKey("sub"), "a service account acts as itself, never through delegation")

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(pair.public)
        verifier.update("${segments[0]}.${segments[1]}".toByteArray())
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(segments[2])))
    }

    @Test
    fun `exchanges the assertion at Google's token endpoint, never the one the key file names, and reuses the token`() {
        val key = ServiceAccountKey.read(PlayTestKeys.keyFile(PlayTestKeys.generate()))
        val transport = FakePlayTransport { ok("""{"access_token":"ya29.synthetic","expires_in":3599,"token_type":"Bearer"}""") }
        val clock = MutableClock(FROZEN)
        val source = AccessTokenSource(key, transport, clock, Transcript { })

        assertEquals("ya29.synthetic", source.token())
        clock.now = FROZEN.plusSeconds(1800)
        assertEquals("ya29.synthetic", source.token())

        val request = transport.requests.single()
        assertEquals("https://oauth2.googleapis.com/token", request.uri.toString())
        assertEquals(WireMethod.POST, request.method)
        val form = String(request.body!!).split("&").associate { pair ->
            val (name, value) = pair.split("=", limit = 2)
            name to URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form["grant_type"])
        assertEquals(3, form.getValue("assertion").split(".").size)
        assertTrue(request.headers.none { (name, _) -> name.equals("Authorization", ignoreCase = true) })

        clock.now = FROZEN.plusSeconds(3590)
        source.token()
        assertEquals(2, transport.requests.size, "a token about to expire is replaced before it is used")
    }

    @Test
    fun `a refused token exchange reports the category without the response text`() {
        val key = ServiceAccountKey.read(PlayTestKeys.keyFile(PlayTestKeys.generate()))
        val transport = FakePlayTransport {
            status(400, """{"error":"invalid_grant","error_description":"Invalid JWT for publisher@posato-test"}""")
        }

        val failure = assertFailsWith<ProvisioningException> { AccessTokenSource(key, transport, MutableClock(FROZEN), Transcript { }).token() }

        assertEquals(ErrorCode.PLAY_TOKEN_FAILED, failure.code)
        assertTrue(failure.message.orEmpty().contains("invalid_grant"))
        assertFalse(failure.message.orEmpty().contains("publisher@"))
    }

    @Test
    fun `refuses a key file other users can read, without naming its path or content`() {
        val file = PlayTestKeys.keyFile(PlayTestKeys.generate(), permissions = "rw-r--r--")

        val failure = assertFailsWith<ProvisioningException> { ServiceAccountKey.read(file) }

        assertEquals(ErrorCode.PLAY_KEY_UNREADABLE, failure.code)
        assertTrue(failure.hint.orEmpty().contains("chmod 600"))
        assertFalse(failure.message.orEmpty().contains(file.toString()))
    }

    @Test
    fun `refuses a symbolic link and a document that is not a service-account key, quoting neither`() {
        val pair = PlayTestKeys.generate()
        val target = PlayTestKeys.keyFile(pair)
        val link = Files.createTempDirectory("keys").resolve("key.json")
        Files.createSymbolicLink(link, target)
        assertEquals(ErrorCode.PLAY_KEY_UNREADABLE, assertFailsWith<ProvisioningException> { ServiceAccountKey.read(link) }.code)

        val wrongType = PlayTestKeys.keyFile(pair)
        Files.writeString(wrongType, wrongType.readText().replace("service_account", "authorized_user"))
        val failure = assertFailsWith<ProvisioningException> { ServiceAccountKey.read(wrongType) }
        assertEquals(ErrorCode.PLAY_KEY_UNREADABLE, failure.code)
        assertFalse(failure.message.orEmpty().contains("BEGIN PRIVATE KEY"))
        assertFalse(failure.message.orEmpty().contains("publisher@"))
    }

    private fun decode(segment: String): JsonObject = ProvisioningJson.lenient.decodeFromString(
        JsonObject.serializer(),
        String(Base64.getUrlDecoder().decode(segment)),
    )

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
}
