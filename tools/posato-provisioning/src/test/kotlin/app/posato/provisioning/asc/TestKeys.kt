package app.posato.provisioning.asc

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Synthetic key material for the token tests.
 *
 * Every key used by a test is generated inside that test run. No fixture file exists, so no real App Store Connect
 * key can ever be committed by resembling one.
 */
object TestKeys {
    fun generate(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    fun toPem(pair: KeyPair): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
    }

    /** The inverse of the production conversion, kept in the tests because production never needs it. */
    fun joseToDer(jose: ByteArray): ByteArray {
        val r = trim(jose.copyOfRange(0, 32))
        val s = trim(jose.copyOfRange(32, 64))
        val body = integer(r) + integer(s)
        val length = if (body.size < 128) byteArrayOf(body.size.toByte()) else byteArrayOf(0x81.toByte(), body.size.toByte())
        return byteArrayOf(0x30) + length + body
    }

    private fun integer(magnitude: ByteArray): ByteArray {
        val padded = if (magnitude.first().toInt() and 0x80 != 0) byteArrayOf(0) + magnitude else magnitude
        return byteArrayOf(0x02, padded.size.toByte()) + padded
    }

    private fun trim(component: ByteArray): ByteArray {
        val first = component.indexOfFirst { byte -> byte.toInt() != 0 }
        return if (first <= 0) component else component.copyOfRange(first, component.size)
    }
}
