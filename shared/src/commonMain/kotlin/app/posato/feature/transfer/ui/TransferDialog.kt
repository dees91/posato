package app.posato.feature.transfer.ui

import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.transfer.data.TransferPreview
import app.posato.feature.transfer.domain.ImportPlanResult
import app.posato.feature.transfer.domain.ImportRefusal
import app.posato.feature.transfer.domain.ImportSummary
import app.posato.feature.transfer.domain.PauseSetFile

/** The one export or import step on screen; the code lives here only while its dialog is open. */
internal sealed interface TransferDialog {
    data class Export(
        val contents: String,
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
        val failure: String? = null,
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
        val notice: String? = null,
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
        val title: String,
        val text: String,
    ) : TransferDialog
}

/** "3 sets · 12 websites · 2 schedules", as the export dialog and the file summary show it. */
internal fun contentsOf(file: PauseSetFile): String {
    val websites = file.sets.flatMapTo(mutableSetOf()) { set -> set.domains }.size
    return listOf(
        count(file.sets.size, "set", "sets"),
        count(websites, "website", "websites"),
        count(file.schedules.size, "schedule", "schedules"),
    ).joinToString(" · ")
}

/** What an import would do, in one or two sentences under the choice. */
internal fun previewText(result: ImportPlanResult): String {
    return when (result) {
        is ImportPlanResult.Refused -> refusalText(result.reason)

        is ImportPlanResult.Planned -> buildList {
            val summary = result.plan.summary
            if (summary.added.isNotEmpty()) add("Adds ${summary.added.joinToString(", ")}.")
            if (summary.combined.isNotEmpty()) add("Adds websites to ${summary.combined.joinToString(", ")}.")
            if (summary.removed.isNotEmpty()) add("Removes ${summary.removed.joinToString(", ")} and their apps on this device.")
            if (summary.schedules.isNotEmpty()) add("${count(summary.schedules.size, "schedule", "schedules")} arrive switched off.")
            if (summary.removed.isNotEmpty() && summary.firstSetAppsStay) {
                summary.firstSetName?.let { name -> add("$name stays first and keeps this device's apps.") }
            }
            if (isEmpty()) add("Nothing new to add.")
        }.joinToString(" ")
    }
}

private fun refusalText(reason: ImportRefusal): String {
    return when (reason) {
        ImportRefusal.TOO_MANY_SETS -> "This would make more than 10 pause sets. Combine sets with the same name, or replace your sets."
        ImportRefusal.TOO_MANY_WEBSITES -> "This would make more than 1,024 websites. Replace your sets, or remove some websites first."
        ImportRefusal.TOO_MANY_SCHEDULES -> "This would make more than 10 schedules. Replace your schedules, or remove some first."
        ImportRefusal.IN_USE_BY_PAUSE -> "A running pause uses a set or schedule this would remove. You can replace after the pause ends."
    }
}

private fun count(
    value: Int,
    one: String,
    many: String,
): String {
    return if (value == 1) "1 $one" else "$value $many"
}
