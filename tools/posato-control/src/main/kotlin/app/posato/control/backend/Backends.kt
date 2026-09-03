package app.posato.control.backend

import app.posato.control.apple.DeviceBackend
import app.posato.control.apple.SimulatorBackend
import app.posato.control.core.RunContext
import app.posato.control.core.Target
import app.posato.control.desktop.DesktopBackend

object Backends {
    fun create(
        target: Target,
        context: RunContext,
        udid: String?
    ): Backend = when (target) {
        Target.DESKTOP -> DesktopBackend.create(context)
        Target.SIMULATOR -> SimulatorBackend.create(context, udid)
        Target.DEVICE -> DeviceBackend.create(context, udid)
    }
}
