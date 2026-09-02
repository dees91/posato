package app.posato.control.cli

import app.posato.control.core.Target
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path

private const val DEFAULT_TIMEOUT_SECONDS = 120L

class GlobalOptionsGroup : OptionGroup(name = "Common options") {
    private val target by option(
        "--target",
        "-t",
        envvar = "POSATO_CONTROL_TARGET",
        help = "desktop, simulator (sim), or device.",
    ).choice(Target.choices)
    private val udid by option("--udid", help = "Simulator or device identifier (or name) overriding local.properties.")
    private val runId by option("--run-id", help = "Reuse a run directory name instead of generating one.")
    private val artifacts by option("--artifacts", help = "Directory that holds run directories (default: build/verification/runs).").path()
    private val timeout by option("--timeout", help = "Default timeout in seconds for helper commands.").long().default(DEFAULT_TIMEOUT_SECONDS)
    private val human by option("--human", help = "Print a short human summary instead of the JSON envelope.").flag()
    private val verbose by option("--verbose", help = "Echo every helper command and its stderr.").flag()

    fun toGlobalOptions(): GlobalOptions = GlobalOptions(target, udid, runId, artifacts, timeout, human, verbose)
}
