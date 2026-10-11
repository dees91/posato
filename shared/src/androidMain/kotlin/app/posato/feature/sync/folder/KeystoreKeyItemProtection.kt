package app.posato.feature.sync.folder

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the folder workspace's key item with a non-exportable AES key in Android Keystore (ADR 0010), so the file in
 * the application's data directory is useless without this device. A Keystore key that can no longer open the item
 * reads as damaged, and the device removes the workspace and joins again with a new code.
 */
internal class KeystoreKeyItemProtection : KeyItemProtection {
    override fun seal(item: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            byteArrayOf(FORMAT) + cipher.iv + cipher.doFinal(item)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: ProviderException) {
            null
        }
    }

    override fun open(stored: ByteArray): ByteArray? {
        if (stored.size <= HEADER_BYTES || stored[0] != FORMAT) return null
        return try {
            val key = key(create = false) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES))
            cipher.doFinal(stored, HEADER_BYTES, stored.size - HEADER_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: ProviderException) {
            null
        }
    }

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "app.posato.folder.workspace-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT: Byte = 1
        const val IV_BYTES = 12
        const val HEADER_BYTES = 1 + IV_BYTES
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
