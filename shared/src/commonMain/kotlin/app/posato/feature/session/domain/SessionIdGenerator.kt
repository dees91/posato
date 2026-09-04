package app.posato.feature.session.domain

import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncIdentifier
import kotlin.random.Random

internal fun interface SessionIdGenerator {
    fun create(): SessionId
}

internal object RandomSessionIdGenerator : SessionIdGenerator {
    override fun create(): SessionId {
        val bytes = Random.Default.nextBytes(ByteArray(IDENTIFIER_BYTES))
        bytes[VERSION_INDEX] = (bytes[VERSION_INDEX].toInt() and VERSION_MASK or VERSION_VALUE).toByte()
        bytes[VARIANT_INDEX] = (bytes[VARIANT_INDEX].toInt() and VARIANT_MASK or VARIANT_VALUE).toByte()

        return SessionId(checkNotNull(SyncIdentifier.fromUuidV4Bytes(bytes)))
    }
}

private const val IDENTIFIER_BYTES: Int = 16
private const val VERSION_INDEX: Int = 6
private const val VARIANT_INDEX: Int = 8
private const val VERSION_MASK: Int = 0x0F
private const val VERSION_VALUE: Int = 0x40
private const val VARIANT_MASK: Int = 0x3F
private const val VARIANT_VALUE: Int = 0x80
