package app.posato.feature.transfer

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.transfer.data.ExportCode
import app.posato.feature.transfer.data.PauseSetFileSeal
import app.posato.feature.transfer.data.PauseSetOpenResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A coded export: who has the file and the code opens it; anyone else gets a message, and a damaged or newer
 * file is told apart from a wrong code where the container allows it. GCM cannot tell a wrong code from a
 * changed body, so both read the same.
 */
class PauseSetFileSealTest {
    private val plain = """{"format":"posato-pause-sets","version":1,"sets":[],"schedules":[]}""".encodeToByteArray()

    @Test
    fun `given a sealed file when opened with its code typed loosely then the contents come back`() {
        val seal = PauseSetFileSeal(testCrypto())
        val sealed = assertNotNull(seal.seal(plain, "ABCDEFGHJ"))

        val opened = seal.open(sealed, "abc-defg hj")

        assertContentEquals(plain, assertIs<PauseSetOpenResult.Opened>(opened).json)
    }

    @Test
    fun `given a sealed file when opened with another code then it reads as a mismatch`() {
        val seal = PauseSetFileSeal(testCrypto())
        val sealed = assertNotNull(seal.seal(plain, "ABCDEFGHJ"))

        assertEquals(PauseSetOpenResult.CodeMismatch, seal.open(sealed, "ABCDEFGHK"))
    }

    @Test
    fun `given a sealed file with a changed body byte when opened with its code then it reads as a mismatch too`() {
        val seal = PauseSetFileSeal(testCrypto())
        val sealed = assertNotNull(seal.seal(plain, "ABCDEFGHJ"))
        sealed[HEADER_BYTES + 1] = (sealed[HEADER_BYTES + 1].toInt() xor 1).toByte()

        assertEquals(PauseSetOpenResult.CodeMismatch, seal.open(sealed, "ABCDEFGHJ"))
    }

    @Test
    fun `given a truncated or garbled header when opened then it reads as damaged`() {
        val seal = PauseSetFileSeal(testCrypto())
        val sealed = assertNotNull(seal.seal(plain, "ABCDEFGHJ"))

        assertEquals(PauseSetOpenResult.Damaged, seal.open(sealed.copyOf(HEADER_BYTES - 1), "ABCDEFGHJ"))
        assertEquals(PauseSetOpenResult.Damaged, seal.open(sealed.copyOf(HEADER_BYTES + 3), "ABCDEFGHJ"))
    }

    @Test
    fun `given an iteration count other than the version's when opened then it is damaged and no key is derived`() {
        val counting = CountingCrypto(testCrypto())
        val seal = PauseSetFileSeal(counting)
        val sealed = assertNotNull(seal.seal(plain, "ABCDEFGHJ"))
        counting.derivations = 0
        sealed[ITERATIONS_OFFSET + 3] = (sealed[ITERATIONS_OFFSET + 3].toInt() xor 1).toByte()

        assertEquals(PauseSetOpenResult.Damaged, seal.open(sealed, "ABCDEFGHJ"))
        assertEquals(0, counting.derivations)
    }

    @Test
    fun `given a newer container version when opened then it reads as newer`() {
        val seal = PauseSetFileSeal(testCrypto())
        val sealed = assertNotNull(seal.seal(plain, "ABCDEFGHJ"))
        sealed[MAGIC_BYTES] = 2
        val muchNewer = sealed.copyOf().also { bytes -> bytes[MAGIC_BYTES] = 200.toByte() }

        assertEquals(PauseSetOpenResult.NewerVersion, seal.open(sealed, "ABCDEFGHJ"))
        assertEquals(PauseSetOpenResult.NewerVersion, seal.open(muchNewer, "ABCDEFGHJ"))
    }

    @Test
    fun `given a sealed file and no code when opened then it asks for the code`() {
        val seal = PauseSetFileSeal(testCrypto())
        val sealed = assertNotNull(seal.seal(plain, "ABCDEFGHJ"))

        assertEquals(PauseSetOpenResult.CodeNeeded, seal.open(sealed, null))
    }

    @Test
    fun `given a plain file when opened then it passes through without a code`() {
        val opened = PauseSetFileSeal(testCrypto()).open(plain, null)

        assertContentEquals(plain, assertIs<PauseSetOpenResult.Plain>(opened).json)
    }

    @Test
    fun `given generated codes then each has nine characters from the pairing alphabet`() {
        val crypto = FakeSyncCryptoProvider()
        repeat(20) {
            val code = assertNotNull(ExportCode.generate(crypto))
            assertEquals(ExportCode.LENGTH, code.length)
            assertTrue(code.all { character -> character in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }, code)
        }
    }

    @Test
    fun `given typed codes when normalized then case spaces hyphens and look-alike digits are forgiven`() {
        assertEquals("ABCDEFGHJ", ExportCode.normalize(" abc-def ghj "))
        assertEquals("OIBDEFGHJ", ExportCode.normalize("018 DEF GHJ"))
        assertNull(ExportCode.normalize("ABCDEFGH"))
        assertNull(ExportCode.normalize("ABCDEFGH!"))
    }
}

private const val MAGIC_BYTES: Int = 4
private const val ITERATIONS_OFFSET: Int = MAGIC_BYTES + 1
private const val HEADER_BYTES: Int = MAGIC_BYTES + 1 + 4 + 16 + 12

/** Counts key derivations, so a test sees that a refused header never reaches the slow derivation. */
private class CountingCrypto(
    private val delegate: SyncCryptoProvider,
) : SyncCryptoProvider by delegate {
    var derivations: Int = 0

    override fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        length: Int,
    ): ByteArray? {
        derivations++
        return delegate.pbkdf2HmacSha256(password, salt, iterations, length)
    }
}

/**
 * The fake's XOR "HMAC" cancels out over PBKDF2's even round count, so two codes could derive one key; this
 * derivation keeps every byte of the code, which is all these container tests need. Real PBKDF2 is pinned by
 * the JVM vector test.
 */
private fun testCrypto(): SyncCryptoProvider {
    val fake = FakeSyncCryptoProvider()
    return object : SyncCryptoProvider by fake {
        override fun pbkdf2HmacSha256(
            password: ByteArray,
            salt: ByteArray,
            iterations: Int,
            length: Int,
        ): ByteArray {
            return ByteArray(length) { index ->
                (password[index % password.size] * 31 + salt[index % salt.size] + index + iterations).toByte()
            }
        }
    }
}
