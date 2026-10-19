package app.posato.feature.sync.folder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val LINK_PREFIX = "posato://join?code="

/** The link a member shares or shows as a QR code; it carries the same one-time code as the text (ADR 0010). */
internal fun pairingLink(code: String): String {
    return LINK_PREFIX + code
}

/**
 * The code in a pasted or opened join link, or in typed text, or null when it is anything else or its check character
 * does not match. Only the exact join link is read, so no other link or extra part can reach the join.
 */
internal fun pairingCodeFrom(text: String): String? {
    val trimmed = text.trim()
    val candidate = if (trimmed.startsWith(LINK_PREFIX, ignoreCase = true)) trimmed.substring(LINK_PREFIX.length) else trimmed
    val bytes = decodeCode(candidate) ?: return null
    return encodeCode(bytes).also { bytes.fill(0) }
}

/** Join links the operating system opened Posato with, waiting for the sync section to use them. */
internal object PairingLinks {
    private val pending = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = pending.asStateFlow()

    fun open(url: String): Boolean {
        val code = pairingCodeFrom(url) ?: return false
        pending.value = code
        return true
    }

    /** The section took the code into its field; a later link replaces it again. */
    fun consume(code: String) {
        pending.compareAndSet(code, null)
    }
}
