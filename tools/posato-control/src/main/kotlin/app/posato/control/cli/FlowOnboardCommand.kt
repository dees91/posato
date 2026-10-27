package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.model.Actions
import app.posato.control.model.Query
import app.posato.control.model.Step
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Walks first-run onboarding on Android, Linux, or Windows the way a person does: past the welcome and privacy steps, through
 * device access, an optional first website, and the summary to Session. On Android every system request that access
 * raises (the VPN, notifications, the battery exemption) is answered with its allow button, so the grants `install
 * --grants settings` leaves out are given as a person gives them. Windows meets the access step as Linux does, with the
 * same labels, until its own host names its access; the spike jar, which has no pausing service, needs --skip-access.
 * The Mac uses `vm onboard`, iOS its scenario.
 */
class FlowOnboardCommand : ControlCommand("onboard", "Android, Linux, or Windows: walk first-run onboarding to Session, allowing every request.") {
    private val website by option("--website", help = "A first website to add on the website step.")
    private val skipAccess by option("--skip-access", help = "Press Not now on the access step instead of allowing access.").flag()

    override fun execute(session: Session): JsonElement {
        val target = requireTarget(session)
        val backend = session.backend()
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(textContains = " of $STEP_SUMMARY"), timeoutSeconds = LAUNCH_SECONDS)))
        // Onboarding left midway, after a restart or a refused request, goes on from the step it shows.
        var access: String? = null
        repeat(STEP_SUMMARY + 1) {
            when (currentStep(backend)) {
                STEP_WELCOME -> FlowSteps.run(backend, listOf(FlowSteps.button(BEGIN), FlowSteps.waitFor(stepLabel(STEP_PRIVACY))))

                STEP_PRIVACY -> FlowSteps.run(
                    backend,
                    listOf(FlowSteps.reveal(button(CONTINUE)), FlowSteps.tap(button(CONTINUE)), FlowSteps.waitFor(stepLabel(STEP_ACCESS))),
                )

                STEP_ACCESS -> access = access(backend, target)

                STEP_WEBSITE -> website(backend)

                STEP_SUMMARY -> return finish(backend, access)

                else -> throw ControlException(
                    ErrorCode.PRECONDITION_NOT_MET,
                    "Posato does not show onboarding.",
                    "Start from a fresh install or `launch --fresh`.",
                )
            }
        }
        throw ControlException(ErrorCode.ASSERTION_FAILED, "Onboarding did not reach its summary.")
    }

    private fun requireTarget(session: Session): Target {
        val target = session.target()
        if (target != Target.ANDROID && target != Target.LINUX && target != Target.WINDOWS) {
            throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "flow onboard walks the android, linux, and windows targets.",
                "On the Mac run `vm onboard`; on iOS run the onboarding scenario.",
            )
        }
        return target
    }

    private fun stepLabel(step: Int): Query = Query(text = "$step of $STEP_SUMMARY")

    private fun currentStep(backend: Backend): Int? =
        FlowSteps.labels(backend).firstNotNullOfOrNull { STEP.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    private fun access(
        backend: Backend,
        target: Target,
    ): String {
        val outcome = when {
            skipAccess -> NOT_NOW.also { FlowSteps.run(backend, listOf(FlowSteps.reveal(button(NOT_NOW)), FlowSteps.tap(button(NOT_NOW)))) }
            target == Target.ANDROID -> allowAndroid(backend)
            else -> allowLinux(backend)
        }
        if (!skipAccess) FlowSteps.run(backend, listOf(FlowSteps.reveal(button(CONTINUE)), FlowSteps.tap(button(CONTINUE))))
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(text = WEBSITE_TITLE))))
        return outcome
    }

    private fun website(backend: Backend) {
        website?.let { name ->
            FlowSteps.run(
                backend,
                listOf(
                    FlowSteps.typeInto(name, submit = false),
                    FlowSteps.button(ADD),
                    // The receipt reads "1 added · 0 already on your list" once the website is saved.
                    FlowSteps.waitFor(Query(textContains = ADDED_RECEIPT)),
                ),
            )
        }
        // With a website saved the step offers Continue, without one Not now.
        val next = if (FlowSteps.labels(backend).any { SAVED_COUNT.matches(it) }) CONTINUE else NOT_NOW
        FlowSteps.run(backend, listOf(FlowSteps.reveal(button(next)), FlowSteps.tap(button(next)), FlowSteps.waitFor(stepLabel(STEP_SUMMARY))))
    }

    private fun finish(
        backend: Backend,
        access: String?,
    ): JsonElement {
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.reveal(button(GO_TO_SESSION)),
                FlowSteps.tap(button(GO_TO_SESSION)),
                // Session offers Start a session with saved items and Choose paused items without; both are idle.
                FlowSteps.waitFor(Query(text = "NO SESSION ACTIVE")),
                FlowSteps.screenshot("onboarded"),
            ),
        )
        return buildJsonObject {
            access?.let { put("access", it) }
            website?.let { put("website", it) }
        }
    }

    /**
     * Each press of Allow access opens the next missing grant. A system request covers Posato and is answered with
     * its allow button; a Settings page is left with Back, since `install --grants settings` gives those grants.
     */
    private fun allowAndroid(backend: Backend): String {
        repeat(MAX_ANDROID_REQUESTS) {
            val labels = FlowSteps.labels(backend)
            if (ANDROID_ALLOWED in labels) return ANDROID_ALLOWED
            val answer = SYSTEM_ANSWERS.firstOrNull { it in labels }
            when {
                ACCESS_TITLE !in labels && answer != null -> FlowSteps.run(backend, listOf(FlowSteps.button(answer)))
                ACCESS_TITLE !in labels -> FlowSteps.run(backend, listOf(Step(action = Actions.PRESS, key = "back")))
                else -> FlowSteps.run(backend, listOf(FlowSteps.reveal(button(ANDROID_ACTION)), FlowSteps.tap(button(ANDROID_ACTION))))
            }
            FlowSteps.run(backend, listOf(FlowSteps.sleep(REQUEST_SETTLE_SECONDS)))
        }
        throw ControlException(ErrorCode.ASSERTION_FAILED, "Access was not allowed after $MAX_ANDROID_REQUESTS requests.", "Run `doctor -t android`.")
    }

    /**
     * The service installs through `pkexec`; the clone's polkit rule lets the test user approve without a password.
     * A clone that already has the service, such as after `launch --fresh`, shows it installed with only Continue.
     */
    private fun allowLinux(backend: Backend): String {
        if (LINUX_INSTALLED in FlowSteps.labels(backend)) return LINUX_INSTALLED
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.reveal(button(LINUX_ACTION)),
                FlowSteps.tap(button(LINUX_ACTION)),
                FlowSteps.waitFor(Query(text = LINUX_INSTALLED), timeoutSeconds = SERVICE_SECONDS),
            ),
        )
        return LINUX_INSTALLED
    }

    private fun button(text: String): Query = Query(text = text, role = FlowSteps.ROLE_BUTTON)

    private companion object {
        const val BEGIN = "Make some space"
        const val CONTINUE = "Continue"
        const val NOT_NOW = "Not now"
        const val ADD = "Add"
        const val GO_TO_SESSION = "Go to Session"
        const val ACCESS_TITLE = "Make room on this device."
        const val WEBSITE_TITLE = "What pulls you away?"
        const val ADDED_RECEIPT = " added · "
        val SAVED_COUNT = Regex("""^\d+ websites? saved$""")
        const val STEP_WELCOME = 1
        const val STEP_PRIVACY = 2
        const val STEP_ACCESS = 3
        const val STEP_WEBSITE = 4
        const val STEP_SUMMARY = 5
        val STEP = Regex("""^(\d) of 5$""")
        const val ANDROID_ACTION = "Allow access"
        const val ANDROID_ALLOWED = "Access allowed"
        const val LINUX_ACTION = "Allow pausing on this computer"
        const val LINUX_INSTALLED = "Pausing service installed"

        /** The allow buttons of Android's VPN, notification, and battery requests, on stock Android and One UI. */
        val SYSTEM_ANSWERS = listOf("Allow", "OK")
        const val MAX_ANDROID_REQUESTS = 12
        const val REQUEST_SETTLE_SECONDS = 1.5
        const val LAUNCH_SECONDS = 60.0
        const val SERVICE_SECONDS = 90.0
    }
}
