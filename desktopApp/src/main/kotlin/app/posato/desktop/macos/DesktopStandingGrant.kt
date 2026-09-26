package app.posato.desktop.macos

import app.posato.feature.onboarding.MacStandingGrant
import app.posato.feature.onboarding.MacStandingGrantState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal interface MacStandingGrantCommands {
    fun grantState(): HelperGrantState

    fun prepareGrant(): HelperResult

    fun grant(): HelperResult

    fun revokeGrant(): HelperResult
}

internal class DesktopStandingGrant(
    private val commands: MacStandingGrantCommands,
    private val ioDispatcher: CoroutineDispatcher,
) : MacStandingGrant {
    override suspend fun read(): MacStandingGrantState {
        return withContext(ioDispatcher) { commands.grantState().toSwitchState() }
    }

    /**
     * The switch never trusts a grant reply: a lost or declined request is settled by reading the
     * grant state back, so it shows only what the daemon confirms.
     */
    override suspend fun setEnabled(enabled: Boolean): MacStandingGrantState {
        return withContext(ioDispatcher) {
            if (enabled) {
                if (commands.prepareGrant().outcome == HelperResult.Outcome.Success) {
                    commands.grant()
                }
            } else {
                commands.revokeGrant()
            }
            commands.grantState().toSwitchState()
        }
    }

    private fun HelperGrantState.toSwitchState(): MacStandingGrantState {
        return when (this) {
            HelperGrantState.Unsupported -> MacStandingGrantState.UNSUPPORTED
            HelperGrantState.Off -> MacStandingGrantState.OFF
            HelperGrantState.On -> MacStandingGrantState.ON
            HelperGrantState.Unknown -> MacStandingGrantState.UNKNOWN
        }
    }
}
