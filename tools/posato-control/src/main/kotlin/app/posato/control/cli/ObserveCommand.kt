package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.core.refuseHostDesktop
import app.posato.control.core.runsInVirtualMachine
import app.posato.control.desktop.ApplicationObservation
import app.posato.control.desktop.BlockingObserver
import app.posato.control.desktop.PageOutcome
import app.posato.control.desktop.WebsiteObservation
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ObservationResult(
    val expect: String,
    val website: WebsiteObservation? = null,
    val application: ApplicationObservation? = null,
)

/** Desktop only: iOS observes blocking with the `observe-*-ios.json` scenarios through SpringBoard and Safari. */
class ObserveCommand :
    ControlCommand("observe", "Desktop in a VM: prove a website and an application are blocked or allowed, as a person would meet them.") {
    private val website by option("--website", help = "URL to request through the system proxy, such as http://example.com/.")
    private val application by option("--application", help = "Application to open, such as Safari.")
    private val expect by option("--expect", help = "blocked or allowed.").choice(BLOCKED, ALLOWED).required()
    private val seconds by option("--seconds", help = "How long an opened application must survive to count as running.")
        .int()
        .default(DEFAULT_SECONDS)

    override fun execute(session: Session): JsonElement {
        validate(session)
        val observer = BlockingObserver(session.context)
        val result = ObservationResult(
            expect = expect,
            website = website?.let { observer.website(it) },
            application = application?.let { observer.application(it, seconds) },
        )
        val element = ControlJson.pretty.encodeToJsonElement(ObservationResult.serializer(), result)
        if (!met(result)) throw ControlException(ErrorCode.ASSERTION_FAILED, "Expected $expect, observed $element.")
        return element
    }

    private fun validate(session: Session) {
        if (website == null && application == null) throw ControlException(ErrorCode.USAGE, "observe needs --website, --application, or both.")
        if (session.target() != Target.DESKTOP) {
            throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "observe drives the desktop target.", "On iOS run observe-blocking-ios.json.")
        }
        refuseHostDesktop(Target.DESKTOP, hostAllowed = false, inVirtualMachine = ::runsInVirtualMachine)
    }

    private fun met(result: ObservationResult): Boolean {
        val blocked = expect == BLOCKED
        val page = if (blocked) PageOutcome.PAUSED else PageOutcome.LOADED
        val websiteMet = result.website?.let { it.outcome == page.name.lowercase() } ?: true
        val applicationMet = result.application?.let { it.running != blocked } ?: true
        return websiteMet && applicationMet
    }

    private companion object {
        const val BLOCKED = "blocked"
        const val ALLOWED = "allowed"
        const val DEFAULT_SECONDS = 8
    }
}
