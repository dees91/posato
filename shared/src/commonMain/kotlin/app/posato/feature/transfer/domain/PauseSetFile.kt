package app.posato.feature.transfer.domain

import app.posato.feature.schedules.data.StoredSchedule
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.PauseSets

/**
 * What an export file holds: pause sets with their websites, the default set, and schedules. Sets are
 * named by references local to the file, so no device's set identifiers leave it, and app choices never do.
 */
internal data class PauseSetFile(
    val sets: List<FileSet>,
    val defaultSet: String?,
    val schedules: List<FileSchedule>,
) {
    override fun toString(): String {
        return "PauseSetFile(redacted)"
    }
}

/** One set in a file; [first] marks the device's first set, which is unnamed until someone names it. */
internal data class FileSet(
    val ref: String,
    val name: String?,
    val first: Boolean,
    val domains: List<ExactDomain>,
) {
    override fun toString(): String {
        return "FileSet(redacted)"
    }
}

/** One schedule in a file: minutes from midnight, a weekday mask with Monday as bit 0, and its set's reference. */
internal data class FileSchedule(
    val name: String,
    val weekdays: Int,
    val startMinute: Int,
    val endMinute: Int,
    val set: String,
) {
    override fun toString(): String {
        return "FileSchedule(redacted)"
    }
}

internal object PauseSetFileLimits {
    /** The format name a reader checks before anything else. */
    const val FORMAT: String = "posato-pause-sets"

    /** The content version this release writes and the newest it reads. */
    const val CONTENT_VERSION: Int = 1

    /**
     * Ten sets of 1,024 domains of up to 253 characters, with names, schedules, and JSON punctuation, stay
     * under 3 MiB; 4 MiB leaves room without letting a file grow without bound.
     */
    const val MAX_FILE_BYTES: Int = 4 * 1024 * 1024

    /** How deep JSON may nest; the format needs three levels, and a deep file is refused before parsing. */
    const val MAX_JSON_DEPTH: Int = 16

    const val MAX_REF_LENGTH: Int = 32

    const val FILE_EXTENSION: String = "posato"

    const val MEDIA_TYPE: String = "application/vnd.posato.pause-sets"
}

/**
 * What this device exports: its live sets, the first set first, the default it resolves to, and the live
 * schedules of those sets. Refused sets and schedules wait for a slot in the workspace and stay here.
 */
internal fun exportFile(
    sets: PauseSets,
    schedules: List<StoredSchedule>,
): PauseSetFile {
    val live = sets.sets.filterNot { set -> set.refused }
        .sortedBy { set -> if (set.id == PauseSetId.FIRST) 0 else 1 }
    val refs = live.mapIndexed { index, set -> set.id to "s${index + 1}" }.toMap()
    return PauseSetFile(
        sets = live.map { set -> FileSet(checkNotNull(refs[set.id]), set.name, set.id == PauseSetId.FIRST, set.domains) },
        defaultSet = sets.resolvedDefault()?.let(refs::get),
        schedules = schedules.filterNot { schedule -> schedule.refused }.mapNotNull { schedule ->
            refs[schedule.plan.setId]?.let { ref ->
                FileSchedule(schedule.plan.name, schedule.plan.weekdays, schedule.plan.startMinute, schedule.plan.endMinute, ref)
            }
        },
    )
}
