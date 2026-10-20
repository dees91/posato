package app.posato.control.android

import app.posato.control.backend.Backend
import app.posato.control.backend.Evidence
import app.posato.control.backend.Interaction
import app.posato.control.backend.Lifecycle
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target

/** Posato on an Android phone or emulator, chosen with `--udid <adb serial>` or `posato.android.serial`. */
class AndroidBackend private constructor(
    private val androidLifecycle: AndroidLifecycle,
    evidence: Evidence,
    interaction: Interaction,
) : Backend,
    Lifecycle by androidLifecycle,
    Evidence by evidence,
    Interaction by interaction {
    override val target: Target = Target.ANDROID

    /** The grants `install` gives; see [AndroidGrants]. */
    var grants: AndroidGrants
        get() = androidLifecycle.grants
        set(value) {
            androidLifecycle.grants = value
        }

    companion object {
        fun create(
            context: RunContext,
            serial: String?,
        ): AndroidBackend {
            val device = AndroidDevice(context, serial)
            val lifecycle = AndroidLifecycle(context, device, RunStateStore(context.layout))
            val evidence = AndroidEvidence(context, device)
            return AndroidBackend(lifecycle, evidence, AndroidInteraction(context, device, lifecycle, evidence))
        }
    }
}
