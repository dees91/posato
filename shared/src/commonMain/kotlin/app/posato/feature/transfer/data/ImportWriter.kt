package app.posato.feature.transfer.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.data.ScheduleIntent
import app.posato.feature.schedules.data.ScheduleSyncLink
import app.posato.feature.schedules.data.deleteScheduleEverywhere
import app.posato.feature.schedules.data.isStorable
import app.posato.feature.schedules.data.readSnapshot
import app.posato.feature.schedules.data.writeIntent
import app.posato.feature.schedules.data.writeSchedule
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.session.data.sweepRetention
import app.posato.feature.sync.bootstrap.BootstrapStoreResult
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.advanceRevisionOrThrow
import app.posato.feature.targets.data.insertIntents
import app.posato.feature.targets.data.pauseSetIntents
import app.posato.feature.targets.data.readStateOrThrow
import app.posato.feature.targets.data.writeSetRows
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.transfer.domain.ImportCurrent
import app.posato.feature.transfer.domain.ImportPlan
import app.posato.feature.transfer.domain.ImportPlanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal sealed interface ImportWriteResult {
    data class Written(
        val plan: ImportPlan,
    ) : ImportWriteResult

    /** Something changed since the preview; nothing was written, and this is what an import would do now. */
    data class Changed(
        val result: ImportPlanResult,
    ) : ImportWriteResult

    /** Whether a workspace is linked could not be read, so nothing was written without its sync intents. */
    data object WorkspaceUnknown : ImportWriteResult

    data object Failed : ImportWriteResult
}

/**
 * The one write path of an import. Under the policy gate and then the schedule gate (the only order any
 * code takes both in), it captures the linked workspace once, plans again from what is stored, and, if
 * that equals the preview, writes the sets, the schedules, and their sync intents in one transaction.
 */
internal class ImportWriter(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
    private val policy: LocalTargetPolicyStore,
    private val schedules: LocalScheduleStore,
    private val link: ScheduleSyncLink,
    private val running: () -> Pair<Set<PauseSetId>, Set<ScheduleId>>,
) {
    /** What a preview plans against: the stored sets and schedules and what a running pause uses now. */
    suspend fun current(): ImportCurrent? {
        return withContext(databaseDispatcher) {
            try {
                database.transactionWithResult { database.readCurrent() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun apply(
        expected: ImportPlan,
        replan: (ImportCurrent) -> ImportPlanResult,
    ): ImportWriteResult {
        return policy.withWriteGate {
            schedules.withWriteGate {
                val workspaceId = when (val captured = link.captureWorkspace()) {
                    is BootstrapStoreResult.Failure -> return@withWriteGate ImportWriteResult.WorkspaceUnknown
                    is BootstrapStoreResult.Success -> captured.value?.context?.workspaceId?.value?.copyBytes()
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    write(expected, replan, workspaceId).also { result ->
                        if (result is ImportWriteResult.Written) {
                            policy.changeAnnouncer()
                            schedules.changeAnnouncer()
                            if (workspaceId != null) {
                                link.syncNow()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun write(
        expected: ImportPlan,
        replan: (ImportCurrent) -> ImportPlanResult,
        workspaceId: ByteArray?,
    ): ImportWriteResult {
        return withContext(databaseDispatcher) {
            try {
                database.transactionWithResult {
                    val state = database.readStateOrThrow()
                    val replanned = replan(database.readCurrent())
                    val plan = (replanned as? ImportPlanResult.Planned)?.plan
                    if (plan != expected) {
                        return@transactionWithResult ImportWriteResult.Changed(replanned)
                    }
                    database.advanceRevisionOrThrow(state.revision)
                    database.writeSetRows(plan.sets)
                    val intents = pauseSetIntents(state.sets, plan.sets)
                    if (workspaceId != null && intents.isNotEmpty()) {
                        database.insertIntents(PolicySyncWrite(workspaceId, intents))
                    }
                    plan.schedulesToRemove.forEach { id ->
                        database.deleteScheduleEverywhere(id)
                        workspaceId?.let { database.writeIntent(it, ScheduleIntent.Remove(id)) }
                    }
                    if (workspaceId == null && plan.schedulesToRemove.isNotEmpty()) {
                        database.scheduleQueries.promoteRefusedSchedules()
                    }
                    plan.schedulesToSave.forEach { schedule ->
                        check(schedule.isStorable())
                        database.writeSchedule(schedule, refused = false)
                        workspaceId?.let { database.writeIntent(it, ScheduleIntent.Put(schedule)) }
                    }
                    database.sweepRetention()
                    ImportWriteResult.Written(plan)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ImportWriteResult.Failed
            }
        }
    }

    private suspend fun PosatoDatabase.readCurrent(): ImportCurrent {
        val (runningSets, runningSchedules) = running()
        val stored = readSnapshot().schedules
        return ImportCurrent(
            sets = readStateOrThrow().sets,
            schedules = stored.filterNot { schedule -> schedule.refused }.map { schedule -> schedule.plan },
            runningSets = runningSets,
            runningSchedules = runningSchedules,
            refusedSchedules = stored.filter { schedule -> schedule.refused }.map { schedule -> schedule.plan.id },
        )
    }
}

/** The stores an import writes, bound once per app; the running pause is read at the moment of each import. */
internal class TransferStorage(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
    private val policy: LocalTargetPolicyStore,
    private val schedules: LocalScheduleStore,
    private val link: ScheduleSyncLink,
) {
    fun writer(running: () -> Pair<Set<PauseSetId>, Set<ScheduleId>>): ImportWriter {
        return ImportWriter(database, databaseDispatcher, policy, schedules, link, running)
    }
}
