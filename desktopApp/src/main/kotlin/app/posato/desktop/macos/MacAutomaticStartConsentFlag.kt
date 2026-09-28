package app.posato.desktop.macos

import app.posato.feature.onboarding.MacAutomaticStartConsent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The consent to automatic starts, kept in this Mac's user defaults under a key that names the wording's
 * version, so a later wording asks again instead of reusing an older answer.
 */
internal class MacAutomaticStartConsentFlag(
    read: () -> Boolean,
    private val write: (Boolean) -> Unit,
) : MacAutomaticStartConsent {
    private val state = MutableStateFlow(read())
    override val given: StateFlow<Boolean> = state.asStateFlow()

    override fun record(given: Boolean) {
        write(given)
        state.value = given
    }
}

internal const val AUTOMATIC_START_CONSENT_KEY: String = "automaticStartConsentV1"
