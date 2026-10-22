package app.posato.feature.transfer

import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosSigningKey
import app.posato.feature.sync.data.IosSyncCryptoProvider
import platform.Foundation.NSData
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** The iPhone's native PBKDF2 against RFC 7914, so a file sealed on an iPhone opens on every other device. */
class IosPbkdf2Test {
    private val crypto = IosSyncCryptoProvider(UnusedProvider)

    @Test
    fun `given the RFC 7914 vectors when derived natively then the keys match`() {
        assertContentEquals(hex(RFC_ONE), crypto.pbkdf2HmacSha256("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 64))
        assertContentEquals(hex(RFC_TWO), crypto.pbkdf2HmacSha256("Password".encodeToByteArray(), "NaCl".encodeToByteArray(), 80_000, 64))
    }
}

/** The native derivation needs none of the Swift provider's calls. */
private object UnusedProvider : IosCryptoProvider {
    override fun randomBytes(count: Int): NSData? = null

    override fun sha256(message: NSData): NSData? = null

    override fun hmacSha256(
        key: NSData,
        message: NSData,
    ): NSData? = null

    override fun sealAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        plaintext: NSData,
    ): NSData? = null

    override fun openAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        ciphertextAndTag: NSData,
    ): NSData? = null

    override fun createSigningKey(): IosSigningKey? = null

    override fun verifyEd25519(
        publicKey: NSData,
        message: NSData,
        signature: NSData,
    ): Boolean = false
}

private fun hex(value: String): ByteArray {
    return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private const val RFC_ONE =
    "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
private const val RFC_TWO =
    "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"
