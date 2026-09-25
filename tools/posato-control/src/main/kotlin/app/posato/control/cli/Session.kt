package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.backend.Backends
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.LocalConfiguration
import app.posato.control.core.RepoLayout
import app.posato.control.core.RunContext
import app.posato.control.core.Target
import app.posato.control.core.refuseHostDesktop
import app.posato.control.core.runsInVirtualMachine
import java.nio.file.Path
import java.time.Duration

class GlobalOptions(
    val target: Target?,
    val udid: String?,
    val runId: String?,
    val artifacts: Path?,
    val timeoutSeconds: Long,
    val human: Boolean,
    val verbose: Boolean,
)

class Session(
    val options: GlobalOptions,
    private val hostDesktopAllowed: Boolean = false,
) {
    val layout: RepoLayout = RepoLayout.discover()
    val configuration: LocalConfiguration = LocalConfiguration.load(layout)
    val context: RunContext = RunContext(
        layout = layout,
        configuration = configuration,
        runId = options.runId ?: RunContext.newRunId(),
        artifactsRoot = options.artifacts?.toAbsolutePath() ?: layout.runsDirectory,
        verbose = options.verbose,
        timeout = Duration.ofSeconds(options.timeoutSeconds),
    )

    private var cachedBackend: Backend? = null

    fun target(): Target = options.target
        ?: throw ControlException(
            ErrorCode.USAGE,
            "This command needs a target.",
            "Pass --target desktop|simulator|device (or export POSATO_CONTROL_TARGET).",
        )

    fun backend(): Backend = cachedBackend ?: create(target()).also { cachedBackend = it }

    /** Backend for an element command; a null selector reuses the cached default backend. */
    fun backend(processSelector: String?): Backend = if (processSelector == null) backend() else create(target(), processSelector)

    fun backend(target: Target): Backend = create(target)

    /** Every backend passes here, so nothing drives the desktop application on the host Mac. */
    private fun create(
        target: Target,
        processSelector: String? = null
    ): Backend {
        refuseHostDesktop(target, hostDesktopAllowed, ::runsInVirtualMachine)
        return Backends.create(target, context, options.udid, processSelector)
    }
}
