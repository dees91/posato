package app.posato.prototype.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.posato.prototype.PrototypeUiState
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeFixtures
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.PrototypeSync
import app.posato.prototype.model.PrototypeSyncStatus
import app.posato.prototype.model.RecoveryAction
import app.posato.prototype.model.SessionAction
import app.posato.prototype.model.SetupAction
import app.posato.prototype.model.reducePrototype
import kotlinx.collections.immutable.persistentListOf

internal data class PrototypePreviewCase(
    val name: String,
    val state: PrototypeUiState
) {
    override fun toString(): String {
        return name
    }
}

internal class PrototypePreviewDataProvider : PreviewParameterProvider<PrototypePreviewCase> {
    override val values: Sequence<PrototypePreviewCase> = prototypePreviewStates().map { (name, state) ->
        PrototypePreviewCase(name, PrototypeUiState(state))
    }.asSequence()
}

private fun prototypePreviewStates(): List<Pair<String, PrototypeState>> {
    val platform = PrototypePlatform.Mac
    val ready = PrototypeFixtures.ready(platform)
    val initial = PrototypeState(platform)
    val privacy = reducePrototype(initial, SetupAction.ShowPrivacy)
    val checking = reducePrototype(privacy, SetupAction.SyncWithICloud)
    val permission = reducePrototype(checking, SetupAction.DiscoverEmptyWorkspace)
    val targets = reducePrototype(permission, SetupAction.GrantPermission)
    val setup = reducePrototype(ready, SessionAction.OpenSetup)
    val review = reducePrototype(setup, SessionAction.Review)
    val active = PrototypeFixtures.active(platform)
    val items = reducePrototype(ready, ItemAction.OpenItems)
    val editor = reducePrototype(items, ItemAction.OpenDomain())
    val picker = reducePrototype(items, ItemAction.OpenApplications)
    val recovery = PrototypeFixtures.recovery(platform)
    val longList = PrototypeFixtures.longList(platform)

    return listOf(
        "Welcome" to initial,
        "Privacy" to privacy,
        "Discovering workspace" to checking,
        "Waiting for key" to PrototypeFixtures.waiting(platform),
        "Permission" to permission,
        "Empty setup" to targets,
        "Completed setup" to ready.copy(surface = PrototypeSurface.Targets),
        "Website editor" to editor,
        "Invalid website" to reducePrototype(editor, ItemAction.SaveDomain("localhost")),
        "Application picker" to picker,
        "Empty application selection" to reducePrototype(picker, ItemAction.SaveApplications(persistentListOf())),
        "Paused items" to items,
        "Ready" to ready,
        "Long list - ready" to longList,
        "Long list - paused items" to reducePrototype(longList, ItemAction.OpenItems),
        "Long list - review" to longList.copy(surface = PrototypeSurface.SessionReview, session = review.session),
        "Long list - active" to longList.copy(surface = PrototypeSurface.Active, session = active.session),
        "Ready needing attention" to recovery,
        "Empty selection" to ready.copy(policy = initial.policy),
        "Duration" to setup,
        "Review" to review,
        "Review needing repair" to recovery.copy(surface = PrototypeSurface.SessionReview, session = review.session),
        "Active" to active,
        "Active needing repair" to reducePrototype(active, RecoveryAction.RemoveMapping),
        "Paused message" to active.copy(surface = PrototypeSurface.Blocked),
        "Early end" to active.copy(surface = PrototypeSurface.EarlyEnd),
        "Recovery" to recovery.copy(surface = PrototypeSurface.Recovery),
    ) + PrototypeSyncStatus.entries.map { status -> "Sync $status" to ready.copy(sync = PrototypeSync(status, pendingWork = true)) }
}
