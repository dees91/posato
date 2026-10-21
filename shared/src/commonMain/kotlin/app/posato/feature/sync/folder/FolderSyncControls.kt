package app.posato.feature.sync.folder

import app.posato.feature.sync.domain.WorkspaceId

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class PairingOffer(
    val code: String,
    val expiresAtMillis: Long,
) {
    override fun toString(): String {
        return "PairingOffer(redacted)"
    }
}

internal sealed interface PairingOfferResult {
    data class Offered(
        val offer: PairingOffer,
    ) : PairingOfferResult

    data object Unavailable : PairingOfferResult
}

internal enum class PairingAcceptResult {
    JOINED,
    INVALID_CODE,
    NOT_FOUND,
    EXPIRED,
    REFUSED,
    WRONG_WORKSPACE,
    UNAVAILABLE,
}

internal enum class FolderChoiceResult {
    CHOSEN,
    NOT_A_FOLDER,
}

/** What the chosen folder holds, read without changing it: before a choice or a read, [UNKNOWN]. */
internal enum class FolderContents {
    UNKNOWN,
    EMPTY,
    WORKSPACE,

    /** A workspace this device removed, which it cannot link to again (`SYNC-025`). */
    REMOVED_COPY,
    UNAVAILABLE,
}

/** One read of the chosen folder: what it holds, its workspace, and whether this device already holds that key. */
internal data class FolderReading(
    val contents: FolderContents,
    val workspaceId: WorkspaceId? = null,
    val keyHere: Boolean = false,
)

/**
 * How this host passes a pairing link on: the system share sheet on a phone, the clipboard on a computer. [share]
 * returns whether the link left Posato.
 */
interface LinkSharing {
    val usesShareSheet: Boolean

    suspend fun share(link: String): Boolean

    object None : LinkSharing {
        override val usesShareSheet: Boolean = false

        override suspend fun share(link: String): Boolean {
            return false
        }
    }
}

/** What the sync section can do with the folder transport of ADR 0010 on this device. */
internal interface FolderSyncControls {
    val supported: Boolean

    val linkSharing: LinkSharing
        get() {
            return LinkSharing.None
        }

    val icloudSupported: Boolean

    /** The chosen folder as the person would recognize it, or null before a choice. */
    val folder: StateFlow<String?>

    val canBrowse: Boolean
        get() {
            return false
        }

    val acceptsTypedPath: Boolean
        get() {
            return true
        }

    suspend fun browse(): String? {
        return null
    }

    fun choose(path: String): FolderChoiceResult

    fun clear()

    suspend fun offer(): PairingOfferResult

    suspend fun dismissOffer()

    suspend fun accept(code: String): PairingAcceptResult

    /** Whether the chosen folder already holds a workspace, from the same read a join makes; nothing is written. */
    suspend fun read(): FolderReading {
        return FolderReading(FolderContents.UNKNOWN)
    }

    /** A phone, where the system camera opens a scanned join link in Posato. */
    val scansCodes: Boolean
        get() {
            return linkSharing.usesShareSheet
        }
}

internal object AppleOnlySync : FolderSyncControls {
    override val supported: Boolean = false
    override val icloudSupported: Boolean = true
    override val folder: StateFlow<String?> = MutableStateFlow(null)

    override fun choose(path: String): FolderChoiceResult {
        return FolderChoiceResult.NOT_A_FOLDER
    }

    override fun clear() {
        return
    }

    override suspend fun offer(): PairingOfferResult {
        return PairingOfferResult.Unavailable
    }

    override suspend fun dismissOffer() {
        return
    }

    override suspend fun accept(code: String): PairingAcceptResult {
        return PairingAcceptResult.UNAVAILABLE
    }
}
