package app.posato.control.cli

import app.posato.control.core.ErrorCode
import app.posato.control.model.Actions
import app.posato.control.model.Query
import app.posato.control.model.States
import app.posato.control.model.Step
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.double
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A release build with an update feed asks "Check for updates automatically?" in a modal alert once setup completes,
 * and again on the first open of a replaced install. The alert blocks every click on the window behind it, so a
 * candidate flow answers it first; an absent alert is not a failure, since it is asked only once.
 */
class UpdateConsentCommand :
    ControlCommand(
        "update-consent",
        "Desktop in a VM: answer the release build's \"Check for updates automatically?\" alert when it appears.",
    ) {
    private val answer by option("--answer", help = "allow (Check Automatically) or deny (Don’t Check).")
        .choice(ALLOW, DENY)
        .required()
    private val timeout by option("--timeout-seconds", help = "How long to wait for the alert.").double().default(DEFAULT_WAIT_SECONDS)

    override fun execute(session: Session): JsonElement {
        requireDesktopInVirtualMachine(session)
        val backend = session.backend()
        val wait = Step(action = Actions.WAIT_FOR, state = States.EXISTS, query = Query(textContains = TITLE), timeoutSeconds = timeout)
        val waited = backend.runScenario(singleStep(wait))
        // Only a timed-out wait means that no alert appeared; any other failure, such as no tracked application, fails.
        val shown = waited.steps.firstOrNull()?.error?.code != ErrorCode.WAIT_TIMEOUT.name
        if (shown) failIfStepFailed(waited)
        if (shown) {
            val button = if (answer == ALLOW) ALLOW_BUTTON else DENY_BUTTON
            failIfStepFailed(backend.runScenario(singleStep(Step(action = Actions.TAP, query = Query(text = button)))))
        }
        return buildJsonObject {
            put("answered", shown)
            if (shown) put("answer", answer)
        }
    }

    private companion object {
        const val ALLOW = "allow"
        const val DENY = "deny"
        const val TITLE = "Check for updates automatically?"
        const val ALLOW_BUTTON = "Check Automatically"
        const val DENY_BUTTON = "Don’t Check"
        const val DEFAULT_WAIT_SECONDS = 10.0
    }
}
