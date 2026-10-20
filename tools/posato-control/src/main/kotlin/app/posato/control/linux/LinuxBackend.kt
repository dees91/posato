package app.posato.control.linux

import app.posato.control.backend.Backend
import app.posato.control.backend.Evidence
import app.posato.control.backend.Interaction
import app.posato.control.backend.Lifecycle
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target

/** Posato in the Tart Ubuntu clone that `linux create` starts. */
class LinuxBackend private constructor(
    lifecycle: Lifecycle,
    evidence: Evidence,
    interaction: Interaction,
) : Backend,
    Lifecycle by lifecycle,
    Evidence by evidence,
    Interaction by interaction {
    override val target: Target = Target.LINUX

    companion object {
        fun create(context: RunContext): LinuxBackend {
            val guest = LinuxGuest(context)
            val lifecycle = LinuxLifecycle(context, guest, RunStateStore(context.layout))
            val evidence = LinuxEvidence(context, guest)
            return LinuxBackend(lifecycle, evidence, LinuxInteraction(context, guest, lifecycle, evidence))
        }
    }
}
