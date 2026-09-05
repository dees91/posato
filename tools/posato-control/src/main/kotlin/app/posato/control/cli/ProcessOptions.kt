package app.posato.control.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option

class ProcessOptions : OptionGroup(name = "Process targeting (desktop only)") {
    private val process by option(
        "--process",
        help = "Address another process inside the staged Posato.app by executable name (PosatoMacOSHelper) or pid. " +
            "Defaults to the tracked application.",
    )

    fun selector(): String? = process?.takeIf { it.isNotBlank() }
}
