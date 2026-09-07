package app.posato.feature.sync.mailbox

internal const val MAILBOX_BUNDLE_IDENTIFIER_BYTES: Int = 16
internal const val MAILBOX_BUNDLE_BYTES: Int = 65_536
internal const val MAILBOX_CURSOR_BYTES: Int = 16_384

internal class MailboxBundle private constructor(
    private val identifier: ByteArray,
    private val payload: ByteArray,
) {
    fun copyIdentifier(): ByteArray {
        return identifier.copyOf()
    }

    fun copyPayload(): ByteArray {
        return payload.copyOf()
    }

    override fun equals(other: Any?): Boolean {
        return other is MailboxBundle &&
            identifier.contentEquals(other.identifier) &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        return 31 * identifier.contentHashCode() + payload.contentHashCode()
    }

    override fun toString(): String {
        return "MailboxBundle(redacted)"
    }

    companion object {
        fun fromParts(
            identifier: ByteArray,
            payload: ByteArray,
        ): MailboxBundle? {
            if (identifier.size != MAILBOX_BUNDLE_IDENTIFIER_BYTES) {
                return null
            }
            if (payload.size !in 1..MAILBOX_BUNDLE_BYTES) {
                return null
            }
            return MailboxBundle(identifier.copyOf(), payload.copyOf())
        }
    }
}

internal class MailboxCursor private constructor(
    private val bytes: ByteArray,
) {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    fun isFirstPage(): Boolean {
        return bytes.isEmpty()
    }

    override fun equals(other: Any?): Boolean {
        return other is MailboxCursor && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "MailboxCursor(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): MailboxCursor? {
            if (bytes.size > MAILBOX_CURSOR_BYTES) {
                return null
            }
            return MailboxCursor(bytes.copyOf())
        }
    }
}

internal data class ChangePage(
    val bundle: MailboxBundle?,
    val moreChanges: Boolean,
    val nextCursor: MailboxCursor,
) {
    override fun toString(): String {
        return "ChangePage(redacted)"
    }
}

internal sealed interface BundleSaveResult {
    data object Saved : BundleSaveResult

    data object Identical : BundleSaveResult

    data object Conflict : BundleSaveResult

    data object Retryable : BundleSaveResult

    data object AccountChanged : BundleSaveResult

    data object UnknownOutcome : BundleSaveResult

    data object IntegrityFailure : BundleSaveResult
}

internal sealed interface ChangeFetchResult {
    data class Page(
        val page: ChangePage,
    ) : ChangeFetchResult

    data object ZoneMissing : ChangeFetchResult

    data object Retryable : ChangeFetchResult

    data object AccountChanged : ChangeFetchResult

    data object UnknownOutcome : ChangeFetchResult

    data object IntegrityFailure : ChangeFetchResult
}

internal sealed interface ZoneDeleteResult {
    data object DeletedAndAbsent : ZoneDeleteResult

    data object Retryable : ZoneDeleteResult

    data object AccountChanged : ZoneDeleteResult

    data object UnknownOutcome : ZoneDeleteResult
}
