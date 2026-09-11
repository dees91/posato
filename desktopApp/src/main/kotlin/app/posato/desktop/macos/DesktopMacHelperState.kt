package app.posato.desktop.macos

import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.MacHelperReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.nio.file.Path

internal class DesktopMacHelperState(
    private val commands: MacHelperCommands,
    private val verifyHelper: () -> Path,
    private val ioDispatcher: CoroutineDispatcher,
    private val openSettings: (URI) -> Unit,
) : MacHelperPort {
    override suspend fun enable(): MacHelperReadiness {
        return readiness {
            val enabled = commands.enable()
            if (enabled.outcome == HelperResult.Outcome.Success &&
                enabled.serviceState == HelperResult.State.Ready
            ) {
                commands.status()
            } else {
                enabled
            }
        }
    }

    override suspend fun recheck(): MacHelperReadiness {
        return readiness { commands.status() }
    }

    override fun openApprovalSettings() {
        try {
            openSettings(URI(LOGIN_ITEMS_SETTINGS))
        } catch (_: IOException) {
            return
        }
    }

    private suspend fun readiness(operation: () -> HelperResult): MacHelperReadiness {
        return withContext(ioDispatcher) {
            if (runCatching { verifyHelper() }.isFailure) {
                return@withContext MacHelperReadiness.UNAVAILABLE
            }
            try {
                operation().toReadiness()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                MacHelperReadiness.UNAVAILABLE
            }
        }
    }

    private fun HelperResult.toReadiness(): MacHelperReadiness {
        return when {
            outcome == HelperResult.Outcome.UnknownOutcome -> {
                MacHelperReadiness.UNCERTAIN
            }

            outcome == HelperResult.Outcome.Success && serviceState == HelperResult.State.Ready -> {
                MacHelperReadiness.READY
            }

            serviceState == HelperResult.State.ApprovalRequired ||
                requiredAction == HelperResult.RequiredAction.BackgroundApproval -> {
                MacHelperReadiness.APPROVAL_REQUIRED
            }

            outcome == HelperResult.Outcome.Failure && serviceState == HelperResult.State.NotRegistered -> {
                MacHelperReadiness.UNAVAILABLE
            }

            serviceState == HelperResult.State.NotRegistered -> {
                MacHelperReadiness.NOT_ENABLED
            }

            isUnlaunchableRegistration() -> {
                MacHelperReadiness.RECOVERY_REQUIRED
            }

            else -> {
                MacHelperReadiness.UNAVAILABLE
            }
        }
    }

    private companion object {
        const val LOGIN_ITEMS_SETTINGS: String = "x-apple.systempreferences:com.apple.LoginItems-Settings.extension"
    }
}
