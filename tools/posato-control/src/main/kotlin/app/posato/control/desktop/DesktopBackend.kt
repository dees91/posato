package app.posato.control.desktop

import app.posato.control.backend.Backend
import app.posato.control.backend.Evidence
import app.posato.control.backend.Interaction
import app.posato.control.backend.Lifecycle
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target

class DesktopBackend private constructor(
    lifecycle: Lifecycle,
    evidence: Evidence,
    interaction: Interaction,
) : Backend,
    Lifecycle by lifecycle,
    Evidence by evidence,
    Interaction by interaction {
    override val target: Target = Target.DESKTOP

    companion object {
        fun create(
            context: RunContext,
            processSelector: String? = null
        ): DesktopBackend {
            val bridge = AxBridge(context)
            val processes = DesktopProcesses(context)
            val stateStore = RunStateStore(context.layout)
            val evidence = DesktopEvidence(context, bridge, processes, stateStore, processSelector)
            val lifecycle = DesktopLifecycle(context, bridge, processes, stateStore, evidence)
            val interaction = DesktopInteraction(context, bridge, processes, stateStore, lifecycle, evidence, processSelector)
            return DesktopBackend(lifecycle, evidence, interaction)
        }
    }
}
