package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EcdsaSignatureTest {
    @Test
    fun `converts a plain thirty-two byte pair`() {
        val r = ByteArray(32) { 0x11 }
        val s = ByteArray(32) { 0x22 }

        val jose = EcdsaSignature.derToJose(sequence(integer(r), integer(s)))

        assertEquals(64, jose.size)
        assertContentEquals(r, jose.copyOfRange(0, 32))
        assertContentEquals(s, jose.copyOfRange(32, 64))
    }

    @Test
    fun `strips the zero byte DER adds to keep a component positive`() {
        val r = ByteArray(32) { 0xF0.toByte() }
        val s = ByteArray(32) { 0x22 }

        val jose = EcdsaSignature.derToJose(sequence(integer(byteArrayOf(0) + r), integer(s)))

        assertContentEquals(r, jose.copyOfRange(0, 32))
        assertContentEquals(s, jose.copyOfRange(32, 64))
    }

    @Test
    fun `left pads a component DER shortened because its leading bytes were zero`() {
        val shortR = ByteArray(31) { 0x11 }
        val shortS = ByteArray(20) { 0x22 }

        val jose = EcdsaSignature.derToJose(sequence(integer(shortR), integer(shortS)))

        assertEquals(0, jose[0])
        assertContentEquals(shortR, jose.copyOfRange(1, 32))
        assertContentEquals(ByteArray(12), jose.copyOfRange(32, 44))
        assertContentEquals(shortS, jose.copyOfRange(44, 64))
    }

    @Test
    fun `accepts the long form sequence length a full signature needs`() {
        val body = integer(byteArrayOf(0) + ByteArray(32) { 0xF0.toByte() }) + integer(byteArrayOf(0) + ByteArray(32) { 0xF0.toByte() })
        val der = byteArrayOf(0x30, 0x81.toByte(), body.size.toByte()) + body

        assertEquals(64, EcdsaSignature.derToJose(der).size)
    }

    @Test
    fun `rejects every malformed encoding instead of producing a wrong token`() {
        val valid = sequence(integer(ByteArray(32) { 0x11 }), integer(ByteArray(32) { 0x22 }))
        val malformed = mapOf(
            "wrong outer tag" to byteArrayOf(0x31) + valid.copyOfRange(1, valid.size),
            "truncated body" to valid.copyOfRange(0, valid.size - 4),
            "trailing bytes" to valid + byteArrayOf(0x00),
            "component is not an integer" to sequence(byteArrayOf(0x04, 32) + ByteArray(32), integer(ByteArray(32) { 0x22 })),
            "component larger than the curve" to sequence(integer(ByteArray(34) { 0x11 }), integer(ByteArray(32) { 0x22 })),
            "empty" to ByteArray(0),
        )

        malformed.forEach { (name, der) ->
            val failure = assertFailsWith<ProvisioningException>(name) { EcdsaSignature.derToJose(der) }
            assertEquals(ErrorCode.ASC_TOKEN_FAILED, failure.code, name)
        }
    }

    @Test
    fun `round trips every signature a real key produces`() {
        val pair = TestKeys.generate()
        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        val verifier = java.security.Signature.getInstance("SHA256withECDSA")

        // A short r or s appears in roughly one signature in a hundred and twenty-eight, so a single sample would
        // pass with the padding handled wrongly. This many samples makes that failure reliable rather than lucky.
        repeat(200) { index ->
            signer.initSign(pair.private)
            signer.update("message $index".toByteArray())
            val jose = EcdsaSignature.derToJose(signer.sign())

            assertEquals(64, jose.size, "sample $index")
            verifier.initVerify(pair.public)
            verifier.update("message $index".toByteArray())
            assertEquals(true, verifier.verify(TestKeys.joseToDer(jose)), "sample $index")
        }
    }

    private fun integer(magnitude: ByteArray): ByteArray = byteArrayOf(0x02, magnitude.size.toByte()) + magnitude

    private fun sequence(vararg parts: ByteArray): ByteArray {
        val body = parts.reduce { left, right -> left + right }
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
