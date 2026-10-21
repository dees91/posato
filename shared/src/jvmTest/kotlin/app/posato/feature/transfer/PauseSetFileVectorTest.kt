package app.posato.feature.transfer

import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.commonPbkdf2HmacSha256
import app.posato.feature.transfer.data.PauseSetFileSeal
import app.posato.feature.transfer.data.PauseSetOpenResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

/**
 * Real-crypto vectors, so a file sealed on one platform opens on every other. The PBKDF2 vectors are RFC
 * 7914's; the file vector was computed independently with Python's hashlib, hmac, and the cryptography
 * package's AESGCM from the layout in the execution record, not by this code.
 */
class PauseSetFileVectorTest {
    @Test
    fun `given the RFC 7914 vectors when derived by the JDK provider then the keys match`() {
        assertRfc7914(JdkSyncCryptoProvider())
    }

    @Test
    fun `given the RFC 7914 vectors when derived by the common implementation then the keys match`() {
        assertRfc7914(CommonPbkdf2(JdkSyncCryptoProvider()))
    }

    @Test
    fun `given the pinned coded file when opened with its code then it gives the pinned contents`() {
        val opened = PauseSetFileSeal(JdkSyncCryptoProvider()).open(hex(PINNED_FILE), "ABCDEFGHJ")

        assertContentEquals(PINNED_JSON.encodeToByteArray(), assertIs<PauseSetOpenResult.Opened>(opened).json)
    }

    @Test
    fun `given the pinned salt and nonce when sealed then the bytes are the pinned file`() {
        val fixed = FixedRandom(JdkSyncCryptoProvider(), ByteArray(16) { it.toByte() }, ByteArray(12) { (100 + it).toByte() })

        assertContentEquals(hex(PINNED_FILE), PauseSetFileSeal(fixed).seal(PINNED_JSON.encodeToByteArray(), "ABCDEFGHJ"))
    }
}

private fun assertRfc7914(crypto: SyncCryptoProvider) {
    assertContentEquals(hex(RFC_ONE), crypto.pbkdf2HmacSha256("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 64))
    assertContentEquals(hex(RFC_TWO), crypto.pbkdf2HmacSha256("Password".encodeToByteArray(), "NaCl".encodeToByteArray(), 80_000, 64))
}

/** The JDK's HMAC with the interface's own PBKDF2, the path a provider without a native one takes. */
private class CommonPbkdf2(
    private val delegate: SyncCryptoProvider,
) : SyncCryptoProvider by delegate {
    override fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        length: Int,
    ): ByteArray? {
        return commonPbkdf2HmacSha256(password, salt, iterations, length)
    }
}

private class FixedRandom(
    private val delegate: SyncCryptoProvider,
    vararg answers: ByteArray,
) : SyncCryptoProvider by delegate {
    private val queue = ArrayDeque(answers.toList())

    override fun randomBytes(count: Int): ByteArray? {
        return queue.removeFirst().also { check(it.size == count) }
    }
}

private fun hex(value: String): ByteArray {
    return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private const val RFC_ONE =
    "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
private const val RFC_TWO =
    "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"
private const val PINNED_JSON = """{"format":"posato-pause-sets","version":1,"sets":[],"schedules":[]}"""
private const val PINNED_FILE =
    "5053583101000927c0000102030405060708090a0b0c0d0e0f6465666768696a6b6c6d6e6f" +
        "b89858a32c7562f9aee456791dfbac691aa28468b8acf31dabce4038c1a4e2c70b680ed030520b3913379d2b0927a74faf8215365b4a5ef4061ccdf5d9628466b8cf489cc576f0d83f1304c406c6c005b77317"
