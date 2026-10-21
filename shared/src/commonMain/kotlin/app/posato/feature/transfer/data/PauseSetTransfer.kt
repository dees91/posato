package app.posato.feature.transfer.data

import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.domain.RandomScheduleIdGenerator
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleIdGenerator
import app.posato.feature.schedules.domain.ScheduleOccurrences
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.choiceSet
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.ui.PauseSetIdGenerator
import app.posato.feature.targets.ui.RandomPauseSetIdGenerator
import app.posato.feature.transfer.domain.ImportIds
import app.posato.feature.transfer.domain.ImportMode
import app.posato.feature.transfer.domain.ImportPlanResult
import app.posato.feature.transfer.domain.ImportSummary
import app.posato.feature.transfer.domain.PauseSetFile
import app.posato.feature.transfer.domain.exportFile
import app.posato.feature.transfer.domain.planImport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A file ready to save or share, and the code that opens it when it is protected. */
internal class ExportedFile(
    val bytes: ByteArray,
    val code: String?,
) {
    override fun toString(): String {
        return "ExportedFile(redacted)"
    }
}

/** What reading a chosen file gives. */
internal sealed interface TransferRead {
    data class File(
        val file: PauseSetFile,
    ) : TransferRead

    data object CodeNeeded : TransferRead

    data object CodeMismatch : TransferRead

    data object Damaged : TransferRead

    data object NewerVersion : TransferRead

    data object NotPosatoFile : TransferRead

    data object InvalidContents : TransferRead

    data object OverLimit : TransferRead
}

/** A plan shown to the person, with the identifiers new items will get, so confirming plans the same way. */
internal data class TransferPreview(
    val file: PauseSetFile,
    val mode: ImportMode,
    val ids: ImportIds,
    val result: ImportPlanResult,
)

internal sealed interface TransferImport {
    data class Imported(
        val summary: ImportSummary,
        val schedules: List<ScheduleId>,
    ) : TransferImport

    /** Something changed since the preview; this is the new one, and nothing was written. */
    data class Changed(
        val preview: TransferPreview,
    ) : TransferImport

    data object Failed : TransferImport
}

/**
 * Export and import of pause sets and schedules. The slow code derivation runs on [computation], never on
 * the caller's thread, and the code itself is never stored.
 */
internal class PauseSetTransfer(
    private val policy: LocalTargetPolicyStore,
    private val schedules: LocalScheduleStore,
    private val writer: ImportWriter,
    private val crypto: SyncCryptoProvider,
    private val applicationMappings: LocalApplicationMappings,
    private val zone: ScheduleZone,
    private val now: () -> Long,
    private val computation: CoroutineDispatcher,
    private val setIds: PauseSetIdGenerator = RandomPauseSetIdGenerator,
    private val scheduleIds: ScheduleIdGenerator = RandomScheduleIdGenerator,
) {
    /** What an export would hold now, for the export dialog's summary. */
    suspend fun exportContents(): PauseSetFile? {
        val sets = (policy.read() as? LocalPolicyResult.Success)?.value?.sets ?: return null
        val stored = (schedules.read() as? ScheduleResult.Success)?.value?.schedules ?: return null
        return exportFile(sets, stored)
    }

    suspend fun export(protect: Boolean): ExportedFile? {
        val json = PauseSetFileJson.encode(exportContents() ?: return null)
        if (!protect) {
            return ExportedFile(json, null)
        }
        return withContext(computation) {
            val code = ExportCode.generate(crypto) ?: return@withContext null
            PauseSetFileSeal(crypto).seal(json, code)?.let { sealed -> ExportedFile(sealed, code) }.also { json.fill(0) }
        }
    }

    suspend fun read(
        bytes: ByteArray,
        code: String?,
    ): TransferRead {
        val opened = withContext(computation) { PauseSetFileSeal(crypto).open(bytes, code) }
        val json = when (opened) {
            is PauseSetOpenResult.Plain -> opened.json
            is PauseSetOpenResult.Opened -> opened.json
            PauseSetOpenResult.CodeNeeded -> return TransferRead.CodeNeeded
            PauseSetOpenResult.CodeMismatch -> return TransferRead.CodeMismatch
            PauseSetOpenResult.Damaged -> return TransferRead.Damaged
            PauseSetOpenResult.NewerVersion -> return TransferRead.NewerVersion
        }
        return when (val decoded = PauseSetFileJson.decode(json)) {
            is PauseSetJsonResult.Read -> TransferRead.File(decoded.file)
            is PauseSetJsonResult.Refused -> when (decoded.problem) {
                PauseSetFileProblem.NOT_A_POSATO_FILE -> TransferRead.NotPosatoFile
                PauseSetFileProblem.NEWER_VERSION -> TransferRead.NewerVersion
                PauseSetFileProblem.INVALID_CONTENTS -> TransferRead.InvalidContents
                PauseSetFileProblem.OVER_LIMIT -> TransferRead.OverLimit
            }
        }
    }

    suspend fun preview(
        file: PauseSetFile,
        mode: ImportMode,
        ids: ImportIds = ImportIds.create(file, setIds, scheduleIds),
    ): TransferPreview? {
        val current = writer.current() ?: return null
        return TransferPreview(file, mode, ids, planImport(current, file, mode, ids))
    }

    suspend fun import(preview: TransferPreview): TransferImport {
        val plan = (preview.result as? ImportPlanResult.Planned)?.plan ?: return TransferImport.Failed
        return when (val written = writer.apply(plan) { current -> planImport(current, preview.file, preview.mode, preview.ids) }) {
            is ImportWriteResult.Written -> {
                if (preview.mode == ImportMode.REPLACE) {
                    // A failed read keeps every choice; preparing the next pause drops a removed set's choices later.
                    val remaining = (policy.read() as? LocalPolicyResult.Success)?.value?.sets?.sets
                    remaining?.let { sets -> applicationMappings.retainOnly(sets.mapTo(mutableSetOf()) { set: LocalPauseSet -> set.id.choiceSet() }) }
                }
                TransferImport.Imported(written.plan.summary, written.plan.schedulesToSave.map { schedule -> schedule.id })
            }

            is ImportWriteResult.Changed -> TransferImport.Changed(preview.copy(result = written.result))
            ImportWriteResult.WorkspaceUnknown, ImportWriteResult.Failed -> TransferImport.Failed
        }
    }

    /** The names of imported schedules whose window is open now, so turning them on would start a pause at once. */
    suspend fun openNow(ids: List<ScheduleId>): List<String> {
        val snapshot = (schedules.read() as? ScheduleResult.Success)?.value ?: return emptyList()
        val plans = snapshot.schedules.map { stored -> stored.plan }.filter { plan -> plan.id in ids }.map { plan -> plan.copy(enabled = true) }
        val active = ScheduleOccurrences.active(plans, snapshot.facts, now(), zone).map { occurrence -> occurrence.key.schedule }.toSet()
        return plans.filter { plan -> plan.id in active }.map { plan -> plan.name }
    }

    /** Switches the imported schedules on, each as a normal edit, so a linked device shares them. */
    suspend fun turnOn(ids: List<ScheduleId>): Boolean {
        val snapshot = (schedules.read() as? ScheduleResult.Success)?.value ?: return false
        return snapshot.schedules.map { stored -> stored.plan }.filter { plan -> plan.id in ids && !plan.enabled }.all { plan ->
            schedules.save(plan.copy(enabled = true), workspaceId = null) is ScheduleResult.Success
        }
    }
}
