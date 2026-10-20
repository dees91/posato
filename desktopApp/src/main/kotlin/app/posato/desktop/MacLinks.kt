package app.posato.desktop

import app.posato.openPosatoLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Desktop

/**
 * `posato://` links opened on this Mac, such as a pairing link from AirDrop or a message (ADR 0010). The handler is
 * installed before the window exists, so a link that launched Posato is not lost, and each understood link asks the
 * resident window to show.
 */
internal object MacLinks {
    private val opened = MutableStateFlow(0)
    val windowRequests: StateFlow<Int> = opened.asStateFlow()

    fun install() {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) return
        Desktop.getDesktop().setOpenURIHandler { event ->
            if (openPosatoLink(event.uri.toString())) opened.value += 1
        }
    }
}
