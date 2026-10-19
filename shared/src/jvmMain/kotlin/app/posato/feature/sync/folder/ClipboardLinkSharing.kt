package app.posato.feature.sync.folder

import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** On a computer the link goes to the clipboard, ready to paste into a message or into Posato on the other device. */
internal object ClipboardLinkSharing : LinkSharing {
    override val usesShareSheet: Boolean = false

    override suspend fun share(link: String): Boolean {
        if (GraphicsEnvironment.isHeadless()) return false
        return try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: HeadlessException) {
            false
        }
    }
}
