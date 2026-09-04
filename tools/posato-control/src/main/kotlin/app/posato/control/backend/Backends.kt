package app.posato.control.backend

import app.posato.control.apple.DeviceBackend
import app.posato.control.apple.SimulatorBackend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.Target
import app.posato.control.desktop.DesktopBackend

object Backends {
    fun create(
        target: Target,
        context: RunContext,
        udid: String?,
        processSelector: String? = null
    ): Backend {
        if (target != Target.DESKTOP) refuseSelector(processSelector, target)
        return when (target) {
            Target.DESKTOP -> DesktopBackend.create(context, processSelector)
            Target.SIMULATOR -> SimulatorBackend.create(context, udid)
            Target.DEVICE -> DeviceBackend.create(context, udid)
        }
    }

    private fun refuseSelector(
        processSelector: String?,
        target: Target
    ) {
        if (processSelector == null) return
        throw ControlException(
            ErrorCode.UNSUPPORTED_ON_TARGET,
            "--process is a desktop option; the ${target.id} target addresses the application by bundle identifier, not by pid.",
            "Drop --process, or use -t desktop.",
        )
    }
}
