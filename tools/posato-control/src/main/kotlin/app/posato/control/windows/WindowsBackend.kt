package app.posato.control.windows

import app.posato.control.backend.Backend
import app.posato.control.backend.Evidence
import app.posato.control.backend.Interaction
import app.posato.control.backend.Lifecycle
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target
import java.nio.file.Path

/** Posato in the Tart Windows clone that `windows create` starts. */
class WindowsBackend private constructor(
    private val windowsLifecycle: WindowsLifecycle,
    evidence: Evidence,
    interaction: Interaction,
) : Backend,
    Lifecycle by windowsLifecycle,
    Evidence by evidence,
    Interaction by interaction {
    override val target: Target = Target.WINDOWS

    /** The MSI `install` installs instead of the last one `build -t windows` made. */
    var packageFile: Path?
        get() = windowsLifecycle.packageFile
        set(value) {
            windowsLifecycle.packageFile = value
        }

    /** The version `build -t windows` packages instead of the app's. */
    var packageVersion: String?
        get() = windowsLifecycle.packageVersion
        set(value) {
            windowsLifecycle.packageVersion = value
        }

    /** Removes Posato with `msiexec /x`: [msi], or the product registered as [productName]; returns what was removed. */
    fun uninstall(
        msi: Path?,
        productName: String,
    ): String {
        windowsLifecycle.terminate()
        return windowsLifecycle.installer.uninstall(msi, productName)
    }

    fun productCode(productName: String): String? = windowsLifecycle.installer.productCode(productName)

    companion object {
        fun create(context: RunContext): WindowsBackend {
            val guest = WindowsGuest(context)
            val lifecycle = WindowsLifecycle(context, guest, RunStateStore(context.layout))
            val evidence = WindowsEvidence(context, guest, lifecycle)
            return WindowsBackend(lifecycle, evidence, WindowsInteraction(context, guest, lifecycle, evidence))
        }
    }
}
