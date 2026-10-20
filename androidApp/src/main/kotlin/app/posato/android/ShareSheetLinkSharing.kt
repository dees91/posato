package app.posato.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import app.posato.feature.sync.folder.LinkSharing

/** Passes a pairing link on through the system share sheet: a message, a mail, or Quick Share to the other phone. */
internal class ShareSheetLinkSharing(
    private val context: Context,
) : LinkSharing {
    override val usesShareSheet: Boolean = true

    override suspend fun share(link: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
        return try {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
