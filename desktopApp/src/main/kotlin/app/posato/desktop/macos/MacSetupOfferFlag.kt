package app.posato.desktop.macos

/** Whether the one-time setup offer was dismissed or completed, kept in this Mac's user defaults. */
internal class MacSetupOfferFlag(
    val read: () -> Boolean,
    val write: () -> Unit,
)

internal const val SETUP_OFFER_DISMISSED_KEY: String = "setupOfferDismissed"
