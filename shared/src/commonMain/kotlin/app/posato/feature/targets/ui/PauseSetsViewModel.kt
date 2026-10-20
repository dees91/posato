package app.posato.feature.targets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.posato.feature.onboarding.data.LocalSetupResult
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.schedules.host.ScheduledPause
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.choiceSet
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Pause sets list reads and changes; the running parts say which sets a pause uses now. */
internal class PauseSetsInputs(
    val store: LocalTargetPolicyStore,
    val applicationMappings: LocalApplicationMappings,
    val schedules: LocalScheduleStore,
    val sessionStatus: StateFlow<LocalSessionStatus?>,
    val scheduledPause: StateFlow<ScheduledPause?>,
    val setupStore: LocalSetupStore,
    val linked: () -> Boolean,
    val ids: PauseSetIdGenerator = RandomPauseSetIdGenerator,
)

internal class PauseSetsViewModel(
    private val inputs: PauseSetsInputs,
) : ViewModel() {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val state = MutableStateFlow(PauseSetsUiState())
    private val reads: Flow<Unit> = merge(
        refreshRequests.onStart { emit(Unit) },
        inputs.store.policyChanges,
        inputs.schedules.changes,
        inputs.applicationMappings.invalidations,
    )

    val uiState: StateFlow<PauseSetsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { combine(reads, inputs.sessionStatus, inputs.scheduledPause) { _, _, _ -> }.collect { load() } }
        viewModelScope.launch { showUpdateNoticeOnce() }
    }

    fun create(
        name: String,
        onCreated: (PauseSetId) -> Unit,
    ) {
        val id = inputs.ids.create()
        mutate({ sets -> PauseSets.of(sets.sets + LocalPauseSet(id, name, emptyList()), sets.defaultSetId) }) { onCreated(id) }
    }

    fun rename(
        id: PauseSetId,
        name: String,
    ) {
        mutate({ sets -> PauseSets.of(sets.sets.map { set -> if (set.id == id) set.copy(name = name) else set }, sets.defaultSetId) })
    }

    /** A new set with [id]'s websites and this device's apps for it; other devices choose their own apps. */
    fun duplicate(id: PauseSetId) {
        if (state.value.isSaving) {
            return
        }
        state.update { current -> current.copy(isSaving = true, failure = null) }
        viewModelScope.launch {
            val failure = try {
                inputs.duplicateSet(id, inputs.ids.create(), ::editSets)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                PauseSetsFailure.SAVE_FAILED
            }
            state.update { current -> current.copy(isSaving = false, failure = failure) }
        }
    }

    fun makeDefault(id: PauseSetId) {
        mutate({ sets -> PauseSets.of(sets.sets, id).takeIf { sets.sets.any { set -> set.id == id && !set.refused } } })
    }

    /**
     * Deletes [id] after moving every schedule that uses it, enabled or not, to [moveTo]. The schedules and the
     * running parts are read again here, so a schedule or pause that started using the set while the dialog
     * was open is never left without a set; a failed move stops before the set is touched.
     */
    fun delete(
        id: PauseSetId,
        moveTo: PauseSetId?,
    ) {
        if (state.value.isSaving) {
            return
        }
        state.update { current -> current.copy(isSaving = true, failure = null) }
        viewModelScope.launch {
            val failure = try {
                deleteNow(id, moveTo)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                PauseSetsFailure.SAVE_FAILED
            }
            state.update { current -> current.copy(isSaving = false, failure = failure) }
        }
    }

    fun retry() {
        state.update { current -> current.copy(failure = null) }
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun deleteNow(
        id: PauseSetId,
        moveTo: PauseSetId?,
    ): PauseSetsFailure? {
        val schedules = (inputs.schedules.read() as? ScheduleResult.Success)?.value ?: return PauseSetsFailure.SAVE_FAILED
        if (id in inputs.runningSets(schedules)) {
            return PauseSetsFailure.IN_USE
        }
        val sets = (inputs.store.read() as? LocalPolicyResult.Success)?.value?.sets ?: return PauseSetsFailure.SAVE_FAILED
        if (sets.resolvedDefault() == id) {
            return PauseSetsFailure.SAVE_FAILED
        }
        val users = schedules.schedules.filter { stored -> stored.plan.setId == id }
        if (users.isNotEmpty() && (moveTo == null || moveTo == id)) {
            return PauseSetsFailure.SAVE_FAILED
        }
        users.forEach { stored ->
            val moved = inputs.schedules.save(stored.plan.copy(setId = checkNotNull(moveTo)), workspaceId = null)
            if (moved !is ScheduleResult.Success) {
                return PauseSetsFailure.SAVE_FAILED
            }
        }
        val removed = editSets { sets ->
            PauseSets.of(sets.sets.filterNot { set -> set.id == id }, sets.defaultSetId).takeIf { sets.resolvedDefault() != id }
        }
        if (!removed) {
            return PauseSetsFailure.SAVE_FAILED
        }
        inputs.keepSurvivingChoices()
        return null
    }

    private fun mutate(
        transform: (PauseSets) -> PauseSets?,
        onSaved: () -> Unit = {},
    ) {
        if (state.value.isSaving) {
            return
        }
        state.update { current -> current.copy(isSaving = true, failure = null) }
        viewModelScope.launch {
            val saved = try {
                editSets(transform)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            state.update { current -> current.copy(isSaving = false, failure = if (saved) null else PauseSetsFailure.SAVE_FAILED) }
            if (saved) {
                onSaved()
            }
        }
    }

    /** Applies [transform] to a fresh read, once more after a concurrent write, so a sync merge never loses it. */
    private suspend fun editSets(transform: (PauseSets) -> PauseSets?): Boolean {
        repeat(EDIT_ATTEMPTS) {
            val current = (inputs.store.read() as? LocalPolicyResult.Success)?.value ?: return false
            val next = transform(current.sets) ?: return false
            when (val result = inputs.store.replaceSets(current.revision, next)) {
                is LocalPolicyResult.Success -> return true
                is LocalPolicyResult.Failure -> if (result.reason != LocalPolicyFailure.REVISION_CONFLICT) return false
            }
        }
        return false
    }

    private suspend fun load() {
        val policy = (inputs.store.read() as? LocalPolicyResult.Success)?.value
        val schedules = (inputs.schedules.read() as? ScheduleResult.Success)?.value
        if (policy == null || schedules == null) {
            state.update { current -> current.copy(failure = PauseSetsFailure.LOAD_FAILED) }
            return
        }
        val running = inputs.runningSets(schedules)
        val defaultId = policy.sets.resolvedDefault()
        val rows = policy.sets.sets.map { set ->
            PauseSetRow(
                id = set.id,
                name = displayNameOf(set.name),
                isDefault = set.id == defaultId,
                websiteCount = set.domains.size,
                applicationCount = inputs.applicationMappings.applicationCountOf(set.id),
                schedules = schedules.schedules.filter { stored ->
                    stored.plan.setId == set.id
                }.map { stored -> stored.plan.name }.toPersistentList(),
                refused = set.refused,
                inUse = set.id in running,
            )
        }.sortedWith(pauseSetRowOrder)
        state.update { current ->
            current.copy(
                rows = rows.toPersistentList(),
                hasLoaded = true,
                failure = current.failure.takeUnless { it == PauseSetsFailure.LOAD_FAILED },
            )
        }
    }

    private suspend fun showUpdateNoticeOnce() {
        if (!inputs.linked()) {
            return
        }
        val shown = inputs.setupStore.readPauseSetNoticeShown()
        if (shown is LocalSetupResult.Success && !shown.value) {
            state.update { current -> current.copy(showsUpdateNotice = true) }
            inputs.setupStore.markPauseSetNoticeShown()
        }
    }

    private companion object {
        const val EDIT_ATTEMPTS: Int = 2
    }
}

/**
 * Keeps app choices for the sets left after a deletion. A failed read is not an empty workspace: the
 * choices stay, and launch preparation removes the deleted set's choices later.
 */
private suspend fun PauseSetsInputs.keepSurvivingChoices() {
    val remaining = (store.read() as? LocalPolicyResult.Success)?.value?.sets?.sets ?: return
    applicationMappings.retainOnly(remaining.mapTo(mutableSetOf()) { set -> set.id.choiceSet() })
}

/** Adds a copy of [id] as [copyId] through [editSets]; the apps are copied first and dropped again on failure. */
private suspend fun PauseSetsInputs.duplicateSet(
    id: PauseSetId,
    copyId: PauseSetId,
    editSets: suspend ((PauseSets) -> PauseSets?) -> Boolean,
): PauseSetsFailure? {
    val sets = (store.read() as? LocalPolicyResult.Success)?.value?.sets ?: return PauseSetsFailure.SAVE_FAILED
    val source = sets.sets.firstOrNull { set -> set.id == id && !set.refused } ?: return PauseSetsFailure.SAVE_FAILED
    if (sets.sets.count { set -> !set.refused } >= SyncFormatLimits.MAX_PAUSE_SETS) {
        return PauseSetsFailure.SAVE_FAILED
    }
    // The apps come first, so the list's reload after the new set already counts them.
    applicationMappings.copyChoices(id.choiceSet(), copyId.choiceSet())
    val added = editSets { current ->
        PauseSets.of(current.sets + LocalPauseSet(copyId, copyNameOf(source.name), source.domains), current.defaultSetId)
    }
    if (!added) {
        applicationMappings.clear(copyId.choiceSet())
        return PauseSetsFailure.SAVE_FAILED
    }
    // A sync merge between the two writes keeps app choices only for the sets it knew, so the copy is made again.
    // A copy that cannot be made leaves the new set without apps, which the list then shows.
    applicationMappings.copyChoices(id.choiceSet(), copyId.choiceSet())
    return null
}

/** "<name> (copy)", shortened so the whole name stays within a set name's 80 bytes. */
internal fun copyNameOf(name: String?): String {
    var base = displayNameOf(name)
    while ((base + COPY_SUFFIX).encodeToByteArray().size > ScheduleLimits.MAX_NAME_BYTES) {
        // A whole character at a time, so an emoji is never cut in half.
        base = base.dropLast(if (base.length >= 2 && base.last().isLowSurrogate()) 2 else 1)
    }
    return base.trimEnd() + COPY_SUFFIX
}

private const val COPY_SUFFIX: String = " (copy)"

/** How many apps [id] has chosen on this device, or null when its choices cannot be read. */
internal suspend fun LocalApplicationMappings.applicationCountOf(id: PauseSetId): Int? {
    return when (val loaded = load(id.choiceSet())) {
        is LocalApplicationMappingsLoadResult.Success -> loaded.snapshot.mappings.size
        else -> null
    }
}

/** The sets a pause uses now: the active session's, and each running schedule occurrence's schedule's. */
private fun PauseSetsInputs.runningSets(schedules: ScheduleSnapshot): Set<PauseSetId> {
    val session = (sessionStatus.value as? LocalSessionStatus.Active)?.record?.setId
    val occurrences = scheduledPause.value?.keys.orEmpty().mapNotNull { key ->
        schedules.schedules.firstOrNull { stored -> stored.plan.id == key.schedule }?.plan?.setId
    }
    return setOfNotNull(session) + occurrences
}

/** Every set this device holds as a row with its counts, without schedules or running parts. */
internal suspend fun loadPauseSetRows(
    store: LocalTargetPolicyStore,
    applicationMappings: LocalApplicationMappings,
): List<PauseSetRow> {
    val sets = (store.read() as? LocalPolicyResult.Success)?.value?.sets ?: return emptyList()
    val defaultId = sets.resolvedDefault()
    return sets.sets.map { set ->
        PauseSetRow(
            set.id,
            displayNameOf(set.name),
            set.id == defaultId,
            set.domains.size,
            applicationMappings.applicationCountOf(set.id),
            persistentListOf(),
            set.refused,
            inUse = false,
        )
    }.sortedWith(pauseSetRowOrder)
}
