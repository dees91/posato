package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcedSet
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.data.ApplicationChoiceSet
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.choiceSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.ui.PauseSetRow
import app.posato.feature.targets.ui.displayNameOf
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException

/**
 * The websites and this device's apps of one pause set: [setId], or the default set when it is null. A set
 * that no longer exists has nothing to review or pause.
 */
internal suspend fun loadSessionTargets(
    policyStore: LocalTargetPolicyStore,
    applicationMappings: LocalApplicationMappings,
    setId: PauseSetId? = null,
): SessionTargetsState {
    val state = when (val result = policyStore.read()) {
        is LocalPolicyResult.Success -> result.value
        is LocalPolicyResult.Failure -> return SessionTargetsState(mappings = applicationMappings.loadOrFailure(ApplicationChoiceSet.FIRST))
    }
    val chosen = setId ?: state.sets.resolvedDefault() ?: return SessionTargetsState(sets = state.sets)
    val policy = state.policyOf(chosen) ?: return SessionTargetsState(sets = state.sets, setId = chosen)
    return SessionTargetsState(policy, applicationMappings.loadOrFailure(chosen.choiceSet()), chosen, state.sets)
}

private suspend fun LocalApplicationMappings.loadOrFailure(set: ApplicationChoiceSet): LocalApplicationMappingsLoadResult {
    return try {
        load(set)
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (_: Exception) {
        LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE)
    }
}

internal fun SessionTargetsState.toEnforcedSet(): EnforcedSet {
    val policy = this.policy ?: return EnforcedSet()
    val mappings = (mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings
    return EnforcedSet(
        domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList(),
        applicationCount = mappings?.size,
    )
}

internal fun SessionTargetsState.toFrozenStartSet(): FrozenStartSet {
    val policy = this.policy ?: return FrozenStartSet(persistentListOf(), null)
    val mappings = (mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings
    return FrozenStartSet(
        domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList(),
        applicationCount = mappings?.size,
    )
}

/** What a running session shows: its stored start set, or its set's current items when none was stored. */
internal suspend fun FrozenStartSet?.orLoaded(
    setId: PauseSetId,
    loadTargets: suspend (PauseSetId) -> SessionTargetsState,
): EnforcedSet {
    return this?.toEnforcedSet() ?: loadTargets(setId).toEnforcedSet()
}

/** The live sets a pause can use, as the set choice lists them: the default marked, with website counts. */
internal fun PauseSets?.choices(): PersistentList<PauseSetRow> {
    val sets = this ?: return persistentListOf()
    val defaultId = sets.resolvedDefault()
    return sets.sets.filterNot { set -> set.refused }.map { set ->
        PauseSetRow(set.id, displayNameOf(set.name), set.id == defaultId, set.domains.size, null, persistentListOf(), refused = false, inUse = false)
    }.toPersistentList()
}

/** The name [setId] shows, or null when this device does not hold that set. */
internal fun SessionTargetsState.nameOf(setId: PauseSetId?): String? {
    return sets?.sets?.firstOrNull { set -> set.id == setId }?.let { set -> displayNameOf(set.name) }
}

/** The set the screen is about: the running session's own set, else the one being chosen. */
internal fun SessionTargetsState.partSet(active: LocalSessionStatus.Active?): PauseSetId? {
    return active?.record?.setId ?: setId
}
