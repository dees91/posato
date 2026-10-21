package app.posato.feature.transfer.domain

import app.posato.feature.targets.domain.ExactDomain

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
