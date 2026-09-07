package app.posato.prototype.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

data class FreePlayGroup(
    val title: String,
    val actions: PersistentList<PrototypeAction>
)

val prototypeFreePlayGroups: PersistentList<FreePlayGroup> = persistentListOf(
    FreePlayGroup("Onboarding", persistentListOf<PrototypeAction>(ResetPrototype).addingAll(SetupAction.entries)),
    FreePlayGroup("Items", persistentListOf(ItemAction.OpenItems, ItemAction.OpenDomain(), ItemAction.OpenApplications)),
    FreePlayGroup(
        "Session",
        persistentListOf(
            SessionAction.OpenSetup,
            SetDuration("45"),
            SessionAction.Review,
            SessionAction.Start,
            SessionAction.OpenBlocked,
            SessionAction.OpenPosato,
            SessionAction.Expire,
        ),
    ),
    FreePlayGroup(
        "Early end and sync",
        persistentListOf<PrototypeAction>(
            SessionAction.RequestEarlyEnd,
            SessionAction.CancelEarlyEnd,
            SessionAction.ConfirmEarlyEnd,
        ).addingAll(SyncAction.entries),
    ),
    FreePlayGroup("Device recovery", RecoveryAction.entries.toPersistentList()),
)

fun runPrototypeFreePlay(
    state: PrototypeState,
    action: PrototypeAction
): PrototypeState {
    val direct = reducePrototype(state, action)
    if (direct.outcome.tone != OutcomeTone.Blocked) return direct
    val prepared = reducePrototype(freePlayPrerequisites(state.platform, action), action)

    return prepared.withOutcome("Free play prepared the required context. ${prepared.outcome.message}", prepared.outcome.tone)
}

private fun freePlayPrerequisites(
    platform: PrototypePlatform,
    action: PrototypeAction
): PrototypeState {
    return when (action) {
        ResetPrototype -> PrototypeState(platform)
        is SetupAction -> setupPrerequisites(platform, action)
        is SessionAction -> sessionPrerequisites(platform, action)
        is SetDuration -> reducePrototype(PrototypeFixtures.ready(platform), SessionAction.OpenSetup)
        is SyncAction -> syncPrerequisites(platform, action)
        is RecoveryAction -> recoveryPrerequisites(platform, action)
        is ItemAction -> reducePrototype(PrototypeFixtures.ready(platform), ItemAction.OpenItems)
    }
}

private fun setupPrerequisites(
    platform: PrototypePlatform,
    action: SetupAction
): PrototypeState {
    val initial = PrototypeState(platform)
    val privacy = reducePrototype(initial, SetupAction.ShowPrivacy)
    val checking = reducePrototype(privacy, SetupAction.SyncWithICloud)
    val permission = reducePrototype(checking, SetupAction.DiscoverEmptyWorkspace)
    val targets = reducePrototype(permission, SetupAction.GrantPermission)

    return when (action) {
        SetupAction.ShowPrivacy -> initial
        SetupAction.SyncWithICloud -> privacy
        SetupAction.DiscoverEmptyWorkspace, SetupAction.DiscoverDelayedKey -> checking
        SetupAction.KeyArrived -> PrototypeFixtures.waiting(platform)
        SetupAction.GrantPermission -> permission
        SetupAction.AddExampleDomain, SetupAction.MapExampleApplication -> targets
        SetupAction.FinishOnboarding -> reducePrototype(reducePrototype(targets, SetupAction.AddExampleDomain), SetupAction.MapExampleApplication)
    }
}

private fun sessionPrerequisites(
    platform: PrototypePlatform,
    action: SessionAction
): PrototypeState {
    val ready = PrototypeFixtures.ready(platform)
    val active = PrototypeFixtures.active(platform)

    return when (action) {
        SessionAction.OpenSetup, SessionAction.ReturnToSession -> ready
        SessionAction.Review -> reducePrototype(ready, SessionAction.OpenSetup)
        SessionAction.Start -> reducePrototype(reducePrototype(ready, SessionAction.OpenSetup), SessionAction.Review)
        SessionAction.OpenBlocked, SessionAction.RequestEarlyEnd, SessionAction.Expire -> active
        SessionAction.OpenPosato -> reducePrototype(active, SessionAction.OpenBlocked)
        SessionAction.CancelEarlyEnd, SessionAction.ConfirmEarlyEnd -> reducePrototype(active, SessionAction.RequestEarlyEnd)
    }
}

private fun syncPrerequisites(
    platform: PrototypePlatform,
    action: SyncAction
): PrototypeState {
    val pending = PrototypeFixtures.ready(platform).pending("Free play prepared pending local work.")
    val syncing = reducePrototype(pending, SyncAction.Start)

    return when (action) {
        SyncAction.Start -> pending
        SyncAction.Succeed, SyncAction.Fail -> syncing
        SyncAction.Retry -> reducePrototype(syncing, SyncAction.Fail)
    }
}

private fun recoveryPrerequisites(
    platform: PrototypePlatform,
    action: RecoveryAction
): PrototypeState {
    val ready = PrototypeFixtures.ready(platform)

    return when (action) {
        RecoveryAction.RevokePermission, RecoveryAction.RemoveMapping -> ready
        RecoveryAction.RepairPermission -> reducePrototype(ready, RecoveryAction.RevokePermission)
        RecoveryAction.RemapApplication -> reducePrototype(ready, RecoveryAction.RemoveMapping)
    }
}
