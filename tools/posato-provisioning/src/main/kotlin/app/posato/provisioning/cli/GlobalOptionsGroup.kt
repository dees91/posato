package app.posato.provisioning.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

data class GlobalOptions(
    val human: Boolean,
    val verbose: Boolean,
)

class GlobalOptionsGroup : OptionGroup(name = "Common options") {
    private val human by option("--human", help = "Print a short human summary instead of the JSON envelope.").flag()
    private val verbose by option("--verbose", help = "Echo every helper command and every request, with secrets redacted.").flag()

    fun toGlobalOptions(): GlobalOptions = GlobalOptions(human, verbose)
}
