package app.posato.provisioning.asc

import app.posato.provisioning.core.ProvisioningJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FROZEN: Instant = Instant.parse("2026-09-05T12:00:00Z")

class JsonWebTokenTest {
    @Test
    fun `carries the header and claims App Store Connect requires`() {
        val minted = mint()

        val header = decode(minted.value.split(".")[0])
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("KEYID12345", header["kid"]?.jsonPrimitive?.content)
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)

        val claims = decode(minted.value.split(".")[1])
        assertEquals("11112222-3333-4444-5555-666677778888", claims["iss"]?.jsonPrimitive?.content)
        assertEquals("appstoreconnect-v1", claims["aud"]?.jsonPrimitive?.content)
        assertEquals(FROZEN.epochSecond, claims["iat"]?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun `bounds the lifetime and omits the scope claim a team key does not use`() {
        val minted = mint()
        val claims = decode(minted.value.split(".")[1])

        val lifetime = claims["exp"]!!.jsonPrimitive.content.toLong() - claims["iat"]!!.jsonPrimitive.content.toLong()
        assertTrue(lifetime in 1..1200, "lifetime was $lifetime s")
        assertEquals(FROZEN.plusSeconds(lifetime), minted.expiresAt)
        assertFalse(claims.containsKey("scope"))
    }

    @Test
    fun `signs the header and claims so the public key verifies the token`() {
        val pair = TestKeys.generate()
        val minted = JsonWebToken.mint("KEYID12345", "issuer", pair.private, Clock.fixed(FROZEN, ZoneOffset.UTC))
        val segments = minted.value.split(".")

        assertEquals(3, segments.size)
        val signature = Base64.getUrlDecoder().decode(segments[2])
        assertEquals(64, signature.size)

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pair.public)
        verifier.update("${segments[0]}.${segments[1]}".toByteArray())
        assertTrue(verifier.verify(TestKeys.joseToDer(signature)))
    }

    @Test
    fun `reuses one token until it approaches expiry`() {
        val pair = TestKeys.generate()
        val clock = MutableClock(FROZEN)
        val source = TokenSource("KEYID12345", "issuer", pair.private, clock)

        val first = source.token()
        clock.now = FROZEN.plusSeconds(60)
        assertEquals(first, source.token())

        clock.now = FROZEN.plusSeconds(1000)
        assertFalse(first == source.token())
    }

    private fun mint(): MintedToken = JsonWebToken.mint(
        "KEYID12345",
        "11112222-3333-4444-5555-666677778888",
        TestKeys.generate().private,
        Clock.fixed(FROZEN, ZoneOffset.UTC),
    )

    private fun decode(segment: String): JsonObject = ProvisioningJson.lenient.decodeFromString(
        JsonObject.serializer(),
        String(Base64.getUrlDecoder().decode(segment)),
    )
}
