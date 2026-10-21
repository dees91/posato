package app.posato.desktop.linux

import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.linux.status.LinuxStatusArea
import app.posato.di.DesktopApplicationComponents
import app.posato.feature.presence.LinuxPresenceCopy
import kotlinx.coroutines.flow.MutableSharedFlow

/** What a Linux launch hands to the resident application. */
internal class LinuxResidentHost(
    val graph: DesktopApplicationComponents,
    val copy: LinuxPresenceCopy,
    val statusArea: LinuxStatusArea?,
    val residence: LinuxResidence,
    val firstClose: LinuxFirstCloseMarker,
    val startHidden: Boolean,
) {
    /** A second launch or the icon asks for the window; menu actions arrive with their id. */
    val showRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val actions = MutableSharedFlow<PresenceMenuActionId>(extraBufferCapacity = 1)
    val menuOpened = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
