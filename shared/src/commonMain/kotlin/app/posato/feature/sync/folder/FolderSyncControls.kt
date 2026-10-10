package app.posato.feature.sync.folder

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

/** What the sync section can do with the folder transport of ADR 0010 on this device. */
internal interface FolderSyncControls {
    val supported: Boolean

    val icloudSupported: Boolean

    /** The chosen folder as the person would recognize it, or null before a choice. */
    val folder: StateFlow<String?>

    val canBrowse: Boolean
        get() {
            return false
        }

    suspend fun browse(): String? {
        return null
    }

    fun choose(path: String): FolderChoiceResult

    fun clear()

    suspend fun offer(): PairingOfferResult

    suspend fun dismissOffer()

    suspend fun accept(code: String): PairingAcceptResult
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
