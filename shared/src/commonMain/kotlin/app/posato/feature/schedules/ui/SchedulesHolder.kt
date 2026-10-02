package app.posato.feature.schedules.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.data.OccurrenceStop
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.ScheduleStoreFailure
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleIdGenerator
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.ui.PauseSetRow
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * The Schedules destination's state: the stored plans as rows, the editor, and the actions. Everything
 * shown is rebuilt from the store after each change, so another device's edit appears the same way.
 */
@Stable
internal class SchedulesHolder(
    private val store: LocalScheduleStore,
    private val zone: ScheduleZone,
    private val clock: SessionClock,
    private val timeFormat: SessionTimeFormat,
    private val ids: ScheduleIdGenerator,
    private val scope: CoroutineScope,
    private val linked: () -> Boolean = { false },
    private val removedSets: () -> Set<PauseSetId> = { emptySet() },
    private val onSaved: suspend () -> Unit = {},
    private val pauseSets: PauseSetSource = PauseSetSource(),
) {
    var state by mutableStateOf(SchedulesUiState())
        private set
    private var snapshot = ScheduleSnapshot()

    /** Keeps the rows current while the destination is shown. */
    suspend fun run() {
        refresh()
        merge(store.changes, pauseSets.changes).collect { refresh() }
    }

    fun openEditor(row: ScheduleRowModel?) {
        val plan = row?.let { selected -> snapshot.schedules.firstOrNull { it.plan.id == selected.id }?.plan }
        val fresh = ScheduleDraft(setId = state.pauseSets.firstOrNull { set -> set.isDefault }?.id ?: PauseSetId.FIRST)
        state = state.copy(editor = plan?.let(ScheduleDraft::of) ?: fresh, editorError = null)
    }

    fun updateDraft(draft: ScheduleDraft) {
        state = state.copy(editor = draft, editorError = null)
    }

    fun closeEditor() {
        state = state.copy(editor = null, editorError = null, showingSetup = false)
    }

    fun save() {
        val draft = state.editor ?: return
        val problem = draft.problem()
        if (problem != null || state.saving) {
            state = state.copy(editorError = problem ?: state.editorError)
            return
        }
        state = state.copy(saving = true)
        scope.launch {
            val saved = store.save(draft.toPlan(draft.id ?: ids.create()), workspaceId = null)
            state = when (saved) {
                is ScheduleResult.Success -> state.copy(editor = null, editorError = null, saving = false)
                is ScheduleResult.Failure -> state.copy(editorError = saved.reason.toEditorError(), saving = false)
            }
            if (saved is ScheduleResult.Success) {
                refresh()
                onSaved()
            }
        }
    }

    fun setEnabled(
        row: ScheduleRowModel,
        enabled: Boolean,
    ) {
        val plan = snapshot.schedules.firstOrNull { it.plan.id == row.id }?.plan ?: return
        scope.launch {
            refresh(store.save(plan.copy(enabled = enabled), workspaceId = null))
        }
    }

    fun skipNext(row: ScheduleRowModel) {
        val now = clock.currentEpochMillis()
        val key = snapshot.nextKey(row, now, zone) ?: return
        scope.launch {
            refresh(store.stop(setOf(key), OccurrenceStop.SKIP, zone.localAt(now).date, workspaceId = null))
        }
    }

    /** Asks before deleting; a null [id] cancels the question. */
    fun confirmDelete(id: ScheduleId?) {
        state = state.copy(confirmingDelete = id)
    }

    fun delete() {
        val id = state.confirmingDelete ?: return
        state = state.copy(confirmingDelete = null, editor = null)
        scope.launch {
            refresh(store.remove(id, workspaceId = null))
        }
    }

    fun showSetup(visible: Boolean) {
        state = state.copy(showingSetup = visible)
    }

    /** After a list action, [changed] says whether it saved; one that did not leaves the row as it was and says so. */
    private suspend fun refresh(changed: ScheduleResult<*>? = null) {
        changed?.let { state = state.copy(changeFailed = it is ScheduleResult.Failure) }
        val read = store.read() as? ScheduleResult.Success ?: return
        snapshot = read.value
        val sets = pauseSets.load()
        val context = ScheduleSetContext(sets, linked(), pauseSets.deviceNoun, removedSets())
        val rows = buildScheduleRows(snapshot, clock.currentEpochMillis(), zone, timeFormat, context)
        state = state.copy(
            loaded = true,
            schedules = rows.toPersistentList(),
            showUpdateNote = linked() && rows.isEmpty(),
            pauseSets = sets.filterNot(PauseSetRow::refused).toPersistentList(),
        )
    }
}

private fun ScheduleStoreFailure.toEditorError(): ScheduleEditorError {
    return when (this) {
        ScheduleStoreFailure.CAPACITY -> ScheduleEditorError.FULL
        ScheduleStoreFailure.INVALID_SCHEDULE -> ScheduleEditorError.NAME
        ScheduleStoreFailure.STORAGE_FAILURE, ScheduleStoreFailure.WORKSPACE_UNKNOWN -> ScheduleEditorError.NOT_SAVED
    }
}

/** Where the Schedules destination reads the pause sets a schedule can use, and when they change. */
internal class PauseSetSource(
    val changes: Flow<Unit> = emptyFlow(),
    val deviceNoun: String = "device",
    val load: suspend () -> List<PauseSetRow> = { emptyList() },
)
