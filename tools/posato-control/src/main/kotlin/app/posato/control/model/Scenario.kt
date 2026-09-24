package app.posato.control.model

import kotlinx.serialization.Serializable

@Serializable
data class LaunchConfiguration(
    val terminateExisting: Boolean = true,
    val fresh: Boolean = false,
    val arguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
)

@Serializable
data class ScenarioDefaults(
    val timeoutSeconds: Double = DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10.0
    }
}

@Serializable
data class FailurePolicy(
    val screenshot: Boolean = true,
    val snapshot: Boolean = true,
)

@Serializable
data class Step(
    val action: String,
    val name: String? = null,
    val query: Query? = null,
    val state: String? = null,
    val text: String? = null,
    val clear: Boolean = false,
    val submit: Boolean = false,
    val key: String? = null,
    val modifiers: List<String> = emptyList(),
    val seconds: Double? = null,
    val timeoutSeconds: Double? = null,
    val maxDepth: Int? = null,
    val orientation: String? = null,
    val bundleId: String? = null,
    val url: String? = null,
    val secret: String? = null,
    val optional: Boolean = false,
)

@Serializable
data class Scenario(
    val version: Int = 1,
    val launch: LaunchConfiguration = LaunchConfiguration(),
    val defaults: ScenarioDefaults = ScenarioDefaults(),
    val onFailure: FailurePolicy = FailurePolicy(),
    val continueOnFailure: Boolean = false,
    val steps: List<Step> = emptyList(),
)

object Actions {
    const val WAIT_FOR = "waitFor"
    const val TAP = "tap"
    const val TYPE = "type"
    const val PRESS = "press"
    const val ASSERT = "assert"
    const val SCREENSHOT = "screenshot"
    const val SNAPSHOT = "snapshot"
    const val SLEEP = "sleep"
    const val SCROLL_TO = "scrollTo"
    const val TERMINATE = "terminate"
    const val RELAUNCH = "relaunch"
    const val ORIENT = "orient"
    const val LAUNCH_APP = "launchApp"
    const val OPEN_URL = "openURL"
    const val PRESS_KEYS = "pressKeys"
    val iosOnly: Set<String> = setOf(ORIENT, LAUNCH_APP, OPEN_URL, PRESS_KEYS)
    val all: Set<String> = setOf(WAIT_FOR, TAP, TYPE, PRESS, ASSERT, SCREENSHOT, SNAPSHOT, SLEEP, SCROLL_TO, TERMINATE, RELAUNCH) + iosOnly
}

object Orientations {
    const val PORTRAIT = "portrait"
    const val PORTRAIT_UPSIDE_DOWN = "portraitUpsideDown"
    const val LANDSCAPE_LEFT = "landscapeLeft"
    const val LANDSCAPE_RIGHT = "landscapeRight"
    val all: List<String> = listOf(PORTRAIT, PORTRAIT_UPSIDE_DOWN, LANDSCAPE_LEFT, LANDSCAPE_RIGHT)
}

object States {
    const val EXISTS = "exists"
    const val ABSENT = "absent"
    const val ENABLED = "enabled"
    const val DISABLED = "disabled"
    const val SETTLED = "settled"
}
