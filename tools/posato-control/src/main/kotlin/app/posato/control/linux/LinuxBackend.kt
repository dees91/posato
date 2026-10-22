package app.posato.control.linux

import app.posato.control.backend.Backend
import app.posato.control.backend.Evidence
import app.posato.control.backend.Interaction
import app.posato.control.backend.Lifecycle
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target
import java.nio.file.Path

/** Posato in the Tart Ubuntu clone that `linux create` starts. */
class LinuxBackend private constructor(
    private val linuxLifecycle: LinuxLifecycle,
    evidence: Evidence,
    interaction: Interaction,
) : Backend,
    Lifecycle by linuxLifecycle,
    Evidence by evidence,
    Interaction by interaction {
    override val target: Target = Target.LINUX

    /** The `.deb` `install` installs instead of packaging the inputs in the clone. */
    var packageFile: Path?
        get() = linuxLifecycle.packageFile
        set(value) {
            linuxLifecycle.packageFile = value
        }

    companion object {
        fun create(context: RunContext): LinuxBackend {
            val guest = LinuxGuest(context)
            val lifecycle = LinuxLifecycle(context, guest, RunStateStore(context.layout))
            val evidence = LinuxEvidence(context, guest)
            return LinuxBackend(lifecycle, evidence, LinuxInteraction(context, guest, lifecycle, evidence))
        }
    }
}
