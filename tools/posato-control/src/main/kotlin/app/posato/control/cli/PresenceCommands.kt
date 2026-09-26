package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.core.refuseHostDesktop
import app.posato.control.core.runsInVirtualMachine
import app.posato.control.desktop.DesktopPresenceDriver
import app.posato.control.desktop.ResourceSample
import app.posato.control.desktop.StatusMenuResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement

class MenuCommand :
    ControlCommand("menu", "Desktop in a VM: read the status-bar menu, open it through its accessibility press, or choose an item by title.") {
    private val open by option("--open", help = "Open the menu through the status item's accessibility press, as VoiceOver does.").flag()
    private val choose by option("--choose", help = "Choose the menu item with this exact title.")

    override fun execute(session: Session): JsonElement {
        requireDesktopInVirtualMachine(session)
        val mode = when {
            choose != null -> "choose"
            open -> "open"
            else -> "read"
        }
        val result = DesktopPresenceDriver(session.context).menu(mode, choose)
        if (choose != null && result.chosen != true) throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "Could not choose '$choose'.")
        return ControlJson.pretty.encodeToJsonElement(StatusMenuResult.serializer(), result)
    }
}

class CloseWindowCommand :
    ControlCommand("close-window", "Desktop in a VM: press the window's close button, which hides a resident application instead of quitting it.") {
    override fun execute(session: Session): JsonElement? {
        requireDesktopInVirtualMachine(session)
        DesktopPresenceDriver(session.context).closeWindow()
        return null
    }
}

class ResourcesCommand :
    ControlCommand("resources", "Desktop in a VM: sample footprint, CPU time, and idle wakeups of the application and its helper.") {
    private val seconds by option("--seconds", help = "Sample length.").int().default(DEFAULT_SECONDS)

    override fun execute(session: Session): JsonElement {
        requireDesktopInVirtualMachine(session)
        val sample = DesktopPresenceDriver(session.context).resources(seconds)
        return ControlJson.pretty.encodeToJsonElement(ResourceSample.serializer(), sample)
    }

    private companion object {
        const val DEFAULT_SECONDS = 600
    }
}

private fun requireDesktopInVirtualMachine(session: Session) {
    if (session.target() != Target.DESKTOP) {
        throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "This command drives the desktop target.")
    }
    refuseHostDesktop(Target.DESKTOP, hostAllowed = false, inVirtualMachine = ::runsInVirtualMachine)
}
