package app.posato.feature.transfer.ui

import androidx.compose.runtime.Composable
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.transfer.data.TransferPreview
import app.posato.feature.transfer.domain.ImportPlanResult
import app.posato.feature.transfer.domain.ImportRefusal
import app.posato.feature.transfer.domain.ImportSummary
import app.posato.feature.transfer.domain.PauseSetFile
import app.posato.generated.resources.Res
import app.posato.generated.resources.count_schedules
import app.posato.generated.resources.count_schedules_arrive
import app.posato.generated.resources.count_sets
import app.posato.generated.resources.count_websites
import app.posato.generated.resources.count_websites_added
import app.posato.generated.resources.transfer_adds
import app.posato.generated.resources.transfer_adds_websites
import app.posato.generated.resources.transfer_becomes
import app.posato.generated.resources.transfer_contents_join
import app.posato.generated.resources.transfer_in_use
import app.posato.generated.resources.transfer_nothing_to_add
import app.posato.generated.resources.transfer_removed
import app.posato.generated.resources.transfer_removes_apps
import app.posato.generated.resources.transfer_removes_schedules
import app.posato.generated.resources.transfer_schedules_removed
import app.posato.generated.resources.transfer_sets_changed
import app.posato.generated.resources.transfer_stays_first
import app.posato.generated.resources.transfer_switched_off
import app.posato.generated.resources.transfer_too_many_schedules
import app.posato.generated.resources.transfer_too_many_sets
import app.posato.generated.resources.transfer_too_many_websites
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The one export or import step on screen; the code lives here only while its dialog is open. */
internal sealed interface TransferDialog {
    data class Export(
        val file: PauseSetFile,
        val code: String?,
        val busy: Boolean = false,
        val failed: Boolean = false,
    ) : TransferDialog {
        override fun toString(): String {
            return "Export(redacted)"
        }
    }

    data class Code(
        val file: ByteArray,
        val failure: StringResource? = null,
        val busy: Boolean = false,
    ) : TransferDialog {
        override fun equals(other: Any?): Boolean {
            return other is Code && file.contentEquals(other.file) && failure == other.failure && busy == other.busy
        }

        override fun hashCode(): Int {
            return 31 * (31 * file.contentHashCode() + failure.hashCode()) + busy.hashCode()
        }

        override fun toString(): String {
            return "Code(redacted)"
        }
    }

    data class Review(
        val preview: TransferPreview,
        val linked: Boolean,
        val notice: StringResource? = null,
        val busy: Boolean = false,
    ) : TransferDialog

    data class Result(
        val summary: ImportSummary,
        val schedules: List<ScheduleId>,
        val openNow: List<String>,
        val turnedOn: Boolean = false,
        val busy: Boolean = false,
        val failed: Boolean = false,
    ) : TransferDialog

    data class Message(
        val title: StringResource,
        val text: StringResource,
        val argument: String? = null,
    ) : TransferDialog
}

/** "3 sets · 12 websites · 2 schedules", as the export dialog and the file summary show it. */
@Composable
internal fun contentsOf(file: PauseSetFile): String {
    val websites = file.sets.flatMapTo(mutableSetOf()) { set -> set.domains }.size
    return stringResource(
        Res.string.transfer_contents_join,
        pluralStringResource(Res.plurals.count_sets, file.sets.size, file.sets.size),
        pluralStringResource(Res.plurals.count_websites, websites, websites),
        pluralStringResource(Res.plurals.count_schedules, file.schedules.size, file.schedules.size),
    )
}

/** What an import would do, in one or two sentences under the choice. */
@Composable
internal fun previewText(result: ImportPlanResult): String {
    return when (result) {
        is ImportPlanResult.Refused -> refusalText(result.reason)
        is ImportPlanResult.Planned -> plannedPreview(result.plan.summary)
    }
}

@Composable
private fun plannedPreview(summary: ImportSummary): String {
    val added = stringResource(Res.string.transfer_adds, summary.added.joinToString(", "))
    val websites = stringResource(Res.string.transfer_adds_websites, summary.combined.joinToString(", "))
    val removed = stringResource(Res.string.transfer_removes_apps, summary.removed.joinToString(", "))
    val becomes = stringResource(Res.string.transfer_becomes, summary.firstSetWas.orEmpty(), summary.firstSetName.orEmpty())
    val removedSchedules = stringResource(Res.string.transfer_removes_schedules, summary.removedSchedules.joinToString(", "))
    val arriving = pluralStringResource(Res.plurals.count_schedules_arrive, summary.schedules.size, summary.schedules.size)
    val switched = stringResource(Res.string.transfer_switched_off, arriving)
    val stays = stringResource(Res.string.transfer_stays_first, summary.firstSetName.orEmpty())
    val nothing = stringResource(Res.string.transfer_nothing_to_add)
    return buildList {
        if (summary.added.isNotEmpty()) add(added)
        if (summary.combined.isNotEmpty()) add(websites)
        if (summary.removed.isNotEmpty()) add(removed)
        if (summary.firstSetWas != null) add(becomes)
        if (summary.removedSchedules.isNotEmpty()) add(removedSchedules)
        if (summary.schedules.isNotEmpty()) add(switched)
        val removedWhileFirstSetStays = summary.removed.isNotEmpty() && summary.firstSetAppsStay
        val firstSetKeptItsName = summary.firstSetWas == null && summary.firstSetName != null
        if (removedWhileFirstSetStays && firstSetKeptItsName) add(stays)
        if (isEmpty()) add(nothing)
    }.joinToString(" ")
}

/** The lines under a finished import. Each sentence keeps the period the screen already shows. */
@Composable
internal fun changesOf(summary: ImportSummary): List<String> {
    val changed = stringResource(Res.string.transfer_sets_changed, summary.changed.joinToString(", "))
    val removed = stringResource(Res.string.transfer_removed, summary.removed.joinToString(", "))
    val schedules = stringResource(Res.string.transfer_schedules_removed, summary.removedSchedules.joinToString(", "))
    val websites = pluralStringResource(Res.plurals.count_websites_added, summary.websitesAdded, summary.websitesAdded)
    return buildList {
        if (summary.changed.isNotEmpty()) add(changed)
        if (summary.removed.isNotEmpty()) add(removed)
        if (summary.removedSchedules.isNotEmpty()) add(schedules)
        if (summary.websitesAdded > 0) add(websites)
    }
}

@Composable
private fun refusalText(reason: ImportRefusal): String {
    return when (reason) {
        ImportRefusal.TOO_MANY_SETS -> stringResource(Res.string.transfer_too_many_sets)
        ImportRefusal.TOO_MANY_WEBSITES -> stringResource(Res.string.transfer_too_many_websites)
        ImportRefusal.TOO_MANY_SCHEDULES -> stringResource(Res.string.transfer_too_many_schedules)
        ImportRefusal.IN_USE_BY_PAUSE -> stringResource(Res.string.transfer_in_use)
    }
}
