package app.posato.feature.transfer.data

import app.posato.feature.sync.data.SyncCryptoProvider

/** Where an export goes and where an import comes from: the platform's save, share, and open panels. */
public interface PauseSetFilePorts {
    /** Whether this device can save and open export files at all. */
    public val available: Boolean
        get() = true

    /** Saves or shares [bytes] as [fileName]; false when the person cancelled or it could not be written. */
    public suspend fun export(
        bytes: ByteArray,
        fileName: String,
    ): Boolean

    /**
     * The chosen file, read to at most [limit] bytes plus one so that a larger file is refused by size, or
     * null when the person cancelled or it could not be read.
     */
    public suspend fun pick(limit: Int): ByteArray?
}

/** A device without file panels, such as a build without a host for them. */
internal object NoPauseSetFiles : PauseSetFilePorts {
    override val available: Boolean
        get() = false

    override suspend fun export(
        bytes: ByteArray,
        fileName: String,
    ): Boolean {
        return false
    }

    override suspend fun pick(limit: Int): ByteArray? {
        return null
    }
}

/** What each platform gives export and import: its file panels and its cryptography. */
internal class TransferPlatform(
    val files: PauseSetFilePorts,
    val crypto: SyncCryptoProvider,
)
