package app.posato.prototype.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

enum class PrototypeScenario(
    val label: String,
    val description: String
) {
    FirstSession("First session", "First installation, review, pause, local synchronization, and normal expiry."),
    DelayedKey("Delayed key", "An existing workspace waits for its key. No parallel workspace is created."),
    ActionRequired("Action required", "Review catches missing permission and a missing device-local application mapping."),
    EarlyEnd("Early end", "Cancel once, end deliberately, then recover from a failed local synchronization.");

    fun start(platform: PrototypePlatform): PrototypeState {
        return when (this) {
            FirstSession -> PrototypeState(platform)
            DelayedKey -> PrototypeState(platform, workspace = PrototypeWorkspace(remotePolicyAvailable = true))
            ActionRequired -> PrototypeFixtures.recovery(platform)
            EarlyEnd -> PrototypeFixtures.active(platform)
        }
    }

    fun steps(): PersistentList<PrototypeAction> {
        return when (this) {
            FirstSession -> persistentListOf(
                SetupAction.ShowPrivacy,
                SetupAction.SyncWithICloud,
                SetupAction.DiscoverEmptyWorkspace,
                SetupAction.GrantPermission,
                SetupAction.AddExampleDomain,
                SetupAction.MapExampleApplication,
                SetupAction.FinishOnboarding,
                SessionAction.OpenSetup,
                SetDuration("45"),
                SessionAction.Review,
                SessionAction.Start,
                SyncAction.Start,
                SyncAction.Succeed,
                SessionAction.OpenBlocked,
                SessionAction.OpenPosato,
                SessionAction.Expire,
            )

            DelayedKey -> persistentListOf(
                SetupAction.ShowPrivacy,
                SetupAction.SyncWithICloud,
                SetupAction.DiscoverDelayedKey,
                SetupAction.FinishOnboarding,
                SetupAction.KeyArrived,
                SetupAction.GrantPermission,
                SetupAction.MapExampleApplication,
                SetupAction.FinishOnboarding,
            )

            ActionRequired -> persistentListOf(
                SessionAction.OpenSetup,
                SetDuration("45"),
                SessionAction.Review,
                SessionAction.Start,
                RecoveryAction.RepairPermission,
                RecoveryAction.RemapApplication,
                SessionAction.Review,
                SessionAction.Start,
            )

            EarlyEnd -> persistentListOf(
                SessionAction.RequestEarlyEnd,
                SessionAction.CancelEarlyEnd,
                SessionAction.RequestEarlyEnd,
                SessionAction.ConfirmEarlyEnd,
                SyncAction.Start,
                SyncAction.Fail,
                SyncAction.Retry,
                SyncAction.Succeed,
            )
        }
    }

    fun expectsBlocked(index: Int): Boolean {
        val action = steps().getOrNull(index)

        return when (this) {
            DelayedKey -> action == SetupAction.FinishOnboarding && index != steps().lastIndex
            ActionRequired -> action == SessionAction.Start && index != steps().lastIndex
            FirstSession, EarlyEnd -> false
        }
    }
}

enum class PrototypeMoment(
    val label: String
) {
    Ready("Ready"),
    FirstVisit("First visit"),
    Active("Active"),
    Recovery("Recovery"),
    WaitingForKey("Waiting for key");

    fun state(platform: PrototypePlatform): PrototypeState {
        return when (this) {
            Ready -> PrototypeFixtures.ready(platform)
            FirstVisit -> PrototypeState(platform)
            Active -> PrototypeFixtures.active(platform)
            Recovery -> PrototypeFixtures.recovery(platform).copy(surface = PrototypeSurface.Recovery)
            WaitingForKey -> PrototypeFixtures.waiting(platform)
        }
    }
}
