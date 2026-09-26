package app.posato.desktop.macos

import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.MacHelperReadiness
import app.posato.feature.onboarding.MacHelperRemoval
import app.posato.feature.onboarding.MacLoginItem
import app.posato.feature.onboarding.MacStandingGrant
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
    override val loginItem: MacLoginItem? = null,
    override val standingGrant: MacStandingGrant? = null,
) : MacHelperPort {
    override suspend fun enable(): MacHelperReadiness {
        return readiness { enableThenStatus() }
    }

    override suspend fun recheck(): MacHelperReadiness {
        return readiness {
            val status = commands.status()
            if (status.requiresRuleInstallation()) {
                enableThenStatus()
            } else {
                status
            }
        }
    }

    override suspend fun remove(): MacHelperRemoval {
        return withContext(ioDispatcher) {
            if (runCatching { verifyHelper() }.isFailure) {
                return@withContext MacHelperRemoval.CHECK_AGAIN
            }
            if (!loginItemOff()) {
                return@withContext MacHelperRemoval.REMOVE_AGAIN
            }
            try {
                commands.remove().toRemoval()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                MacHelperRemoval.CHECK_AGAIN
            }
        }
    }

    private fun loginItemOff(): Boolean {
        val item = loginItem ?: return true
        item.setEnabled(false)
        return !item.enabled.value
    }

    private fun enableThenStatus(): HelperResult {
        val enabled = commands.enable()
        return if (enabled.outcome == HelperResult.Outcome.Success && enabled.serviceState == HelperResult.State.Ready) {
            commands.status()
        } else {
            enabled
        }
    }

    private fun HelperResult.requiresRuleInstallation(): Boolean {
        return outcome == HelperResult.Outcome.ActionRequired &&
            requiredAction == HelperResult.RequiredAction.RuleRepair &&
            ownershipPhase == HelperResult.Phase.Idle
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

internal fun HelperRemovalAttempt.toRemoval(): MacHelperRemoval {
    val outcome = result.outcome
    return when {
        concernsRemove && outcome == HelperResult.Outcome.Success && result.isRemovedService() -> MacHelperRemoval.REMOVED
        outcome == HelperResult.Outcome.Success -> MacHelperRemoval.REMOVE_AGAIN
        concernsRemove && outcome == HelperResult.Outcome.UnknownOutcome -> MacHelperRemoval.UNCERTAIN
        !concernsRemove -> MacHelperRemoval.CHECK_AGAIN
        result.isUnlaunchableRegistration() -> MacHelperRemoval.CANNOT_START
        result.needsBackgroundApproval() -> MacHelperRemoval.APPROVAL_REQUIRED
        result.isLocalNotEnabledAnswer() -> MacHelperRemoval.NOT_ENABLED
        result.needsProxyAttention() -> MacHelperRemoval.PROXY_ATTENTION
        else -> MacHelperRemoval.REMOVE_AGAIN
    }
}

private fun HelperResult.needsBackgroundApproval(): Boolean {
    return serviceState == HelperResult.State.ApprovalRequired || requiredAction == HelperResult.RequiredAction.BackgroundApproval
}

private fun HelperResult.isRemovedService(): Boolean {
    return serviceState == HelperResult.State.NotRegistered && ownershipPhase == HelperResult.Phase.Idle
}

private fun HelperResult.isLocalNotEnabledAnswer(): Boolean {
    return outcome == HelperResult.Outcome.ActionRequired &&
        (serviceState == HelperResult.State.NotRegistered || serviceState == HelperResult.State.UnavailableOrIncompatible) &&
        ownershipPhase == HelperResult.Phase.RecoveryRequired &&
        requiredAction == HelperResult.RequiredAction.ManualRecovery &&
        failure == HelperResult.Failure.Lifecycle
}

private fun HelperResult.needsProxyAttention(): Boolean {
    val recoveryFailure = requiredAction == HelperResult.RequiredAction.ManualRecovery &&
        (failure == HelperResult.Failure.Integrity || failure == HelperResult.Failure.Storage)
    return recoveryFailure || outcome == HelperResult.Outcome.Conflict
}
