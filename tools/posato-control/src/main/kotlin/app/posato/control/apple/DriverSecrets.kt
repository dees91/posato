package app.posato.control.apple

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.model.Actions
import app.posato.control.model.Scenario

/**
 * Secrets a scenario may type on the device, such as the test iPhone's passcode. A scenario names a secret; the host
 * reads its value from the login Keychain item configured in `local.properties` and hands it to the XCUITest runner
 * only through its environment, so the value never enters the scenario, the result, the envelope, or the transcript.
 */
object DriverSecrets {
    const val DEVICE_PASSCODE = "devicePasscode"
    private const val ENVIRONMENT_PREFIX = "POSATO_SECRET_"
    private val KEY_TAP = Regex("""^\s*t =\s+[\d.]+s\s+(Tap|Find the) ".*" Key\s*$""")

    fun environment(
        scenario: Scenario,
        read: (String) -> String
    ): Map<String, String> = scenario.steps
        .filter { it.action == Actions.PRESS_KEYS }
        .map { step -> step.secret ?: throw ControlException(ErrorCode.SCENARIO_INVALID, "pressKeys needs a secret name.") }
        .distinct()
        .associate { name ->
            if (name != DEVICE_PASSCODE) {
                throw ControlException(ErrorCode.SCENARIO_INVALID, "Unknown secret '$name'.", "The iOS driver knows the secret '$DEVICE_PASSCODE'.")
            }
            ENVIRONMENT_PREFIX + name.uppercase() to read(name)
        }

    /** XCUITest logs every tapped key's label, so a typed passcode would appear digit by digit in the xcodebuild log. */
    fun redactKeyTaps(log: String): String = log.lines().filterNot { KEY_TAP.matches(it) }.joinToString("\n")

    /** Reads a secret from the login Keychain with `security`; the value stays in memory. */
    fun keychainReader(context: RunContext): (String) -> String = { _ ->
        val service = context.configuration.require(
            ConfigurationKey.DEVICE_PASSCODE_KEYCHAIN_SERVICE,
            ErrorCode.SCENARIO_INVALID,
            "Typing the device passcode",
        )
        val account = context.configuration.value(ConfigurationKey.DEVICE_PASSCODE_KEYCHAIN_ACCOUNT)
        val command = buildList {
            addAll(listOf("/usr/bin/security", "find-generic-password", "-s", service))
            account?.let { addAll(listOf("-a", it)) }
            add("-w")
        }
        context.subprocess.run(command)
            .requireSuccess(
                ErrorCode.SCENARIO_INVALID,
                "Reading the device passcode from the Keychain",
                "Store it once with `security add-generic-password -s $service -w`.",
            )
            .stdout
            .trimEnd('\n')
    }
}
