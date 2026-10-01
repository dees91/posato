package app.posato.feature.targets.ui

import androidx.compose.runtime.Immutable
import app.posato.feature.schedules.domain.RandomScheduleIdGenerator
import app.posato.feature.schedules.domain.toSync
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.targets.domain.normalizeApplicationPolicyNameNfc
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/** One row of the Pause sets list. [applicationCount] is null when this device keeps no app choices. */
@Immutable
internal data class PauseSetRow(
    val id: PauseSetId,
    val name: String,
    val isDefault: Boolean,
    val websiteCount: Int,
    val applicationCount: Int?,
    val schedules: PersistentList<String>,
    val refused: Boolean,
    val inUse: Boolean,
) {
    override fun toString(): String {
        return "PauseSetRow(redacted)"
    }
}

internal enum class PauseSetsFailure { LOAD_FAILED, SAVE_FAILED, IN_USE }

@Immutable
internal data class PauseSetsUiState(
    val rows: PersistentList<PauseSetRow> = persistentListOf(),
    val hasLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val failure: PauseSetsFailure? = null,
    val showsUpdateNotice: Boolean = false,
) {
    val canCreate: Boolean
        get() = hasLoaded && !isSaving && rows.count { row -> !row.refused } < SyncFormatLimits.MAX_PAUSE_SETS

    override fun toString(): String {
        return "PauseSetsUiState(redacted)"
    }
}

internal enum class PauseSetNameFailure { EMPTY, TOO_LONG }

/** A set name as schedules store theirs: trimmed, in NFC, 1 to 80 UTF-8 bytes, no control characters. */
internal fun parsePauseSetName(input: String): Pair<String?, PauseSetNameFailure?> {
    val name = normalizeApplicationPolicyNameNfc(input.trim())
    return when {
        name.isEmpty() -> null to PauseSetNameFailure.EMPTY
        !ScheduleWireRules.isValidName(name) -> null to PauseSetNameFailure.TOO_LONG
        else -> name to null
    }
}

/** The name a set shows: its own, or "My set" for the unnamed first set. */
internal fun displayNameOf(name: String?): String {
    return name ?: "My set"
}

internal fun interface PauseSetIdGenerator {
    fun create(): PauseSetId
}

/** New sets get random UUIDv4 identifiers, like schedules, so they are valid on the wire. */
internal object RandomPauseSetIdGenerator : PauseSetIdGenerator {
    override fun create(): PauseSetId {
        return checkNotNull(RandomScheduleIdGenerator.create().toSync()?.value?.let(PauseSetId::of))
    }
}

/** A dialog the list shows: naming a new or existing set, or deleting one. */
internal sealed interface PauseSetDialog {
    data object Create : PauseSetDialog

    data class Rename(
        val row: PauseSetRow,
    ) : PauseSetDialog

    data class Delete(
        val row: PauseSetRow,
    ) : PauseSetDialog
}

/**
 * How lists order sets: the first set on top, then by name with numbers compared as numbers, so "Set 2"
 * comes before "Set 10".
 */
internal val pauseSetRowOrder: Comparator<PauseSetRow> = compareByDescending<PauseSetRow> { row -> row.id == PauseSetId.FIRST }
    .then { left, right -> compareNaturally(left.name.lowercase(), right.name.lowercase()) }

private fun compareNaturally(
    left: String,
    right: String,
): Int {
    val leftParts = NATURAL_PARTS.findAll(left).map(MatchResult::value).toList()
    val rightParts = NATURAL_PARTS.findAll(right).map(MatchResult::value).toList()
    leftParts.zip(rightParts).forEach { (a, b) ->
        val order = if (a.first().isDigit() && b.first().isDigit()) compareNumbers(a, b) else a.compareTo(b)
        if (order != 0) {
            return order
        }
    }
    return leftParts.size.compareTo(rightParts.size)
}

private fun compareNumbers(
    left: String,
    right: String,
): Int {
    val a = left.trimStart('0')
    val b = right.trimStart('0')
    return compareValuesBy(a, b, String::length, { it })
}

private val NATURAL_PARTS: Regex = Regex("\\d+|\\D+")
