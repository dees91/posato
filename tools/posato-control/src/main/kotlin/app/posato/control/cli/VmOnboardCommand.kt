package app.posato.control.cli

import app.posato.control.vm.GuestOnboarding
import app.posato.control.vm.VmLine
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonElement

class VmOnboardCommand :
    ControlCommand("onboard", "Launch the package in a clone and finish first-run onboarding with the helper, answering its prompts.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, legacy, or ventura.").default(VmLine.PRIMARY.id)
    private val timeoutSeconds by option("--timeout-seconds", help = "How long setup may take.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement = GuestOnboarding(session.context).run(
        VmLine.parse(lineOption),
        timeoutSeconds * MILLIS_PER_SECOND,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 420L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
