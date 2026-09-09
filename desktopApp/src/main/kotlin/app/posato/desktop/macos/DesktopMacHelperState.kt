package app.posato.desktop.macos

import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.MacHelperReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.file.Path

internal class DesktopMacHelperState(
    private val commands: MacHelperCommands,
    private val verifyHelper: () -> Path,
    private val ioDispatcher: CoroutineDispatcher,
    private val openSettings: (URI) -> Unit,
) : MacHelperPort {
    override suspend fun enable(): MacHelperReadiness {
        return withContext(ioDispatcher) {
            if (runCatching { verifyHelper() }.isFailure) {
                return@withContext MacHelperReadiness.UNAVAILABLE
            }
            try {
                commands.enable()
                commands.status().toReadiness()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                MacHelperReadiness.UNAVAILABLE
            }
        }
    }

    override suspend fun recheck(): MacHelperReadiness {
        return withContext(ioDispatcher) {
            try {
                commands.status().toReadiness()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                MacHelperReadiness.UNAVAILABLE
            }
        }
    }

    override fun openApprovalSettings() {
        openSettings(URI(LOGIN_ITEMS_SETTINGS))
    }

    private fun HelperResult.toReadiness(): MacHelperReadiness {
        return when {
            outcome == HelperResult.Outcome.Success && serviceState == HelperResult.State.Ready -> {
                MacHelperReadiness.READY
            }

            serviceState == HelperResult.State.ApprovalRequired ||
                requiredAction == HelperResult.RequiredAction.BackgroundApproval -> {
                MacHelperReadiness.APPROVAL_REQUIRED
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
