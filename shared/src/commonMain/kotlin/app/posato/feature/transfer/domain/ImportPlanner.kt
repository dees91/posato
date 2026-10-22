package app.posato.feature.transfer.domain

import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleIdGenerator
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.ui.PauseSetIdGenerator
import app.posato.feature.targets.ui.displayNameOf

/** How a file joins what this device keeps. */
internal enum class ImportMode {
    /** Add; a set with the same name gains the file's websites. */
    COMBINE,

    /** Add; every file set is a new set. */
    SEPARATE,

    /** This device's sets and schedules become the file's. */
    REPLACE,
}

/** What the planner reads: this device's sets and schedules and what a running pause uses now. */
internal data class ImportCurrent(
    val sets: PauseSets,
    val schedules: List<SchedulePlan>,
    val runningSets: Set<PauseSetId>,
    val runningSchedules: Set<ScheduleId>,
    /** Schedules the workspace refused at its limit: not counted, but removed by replace. */
    val refusedSchedules: List<ScheduleId> = emptyList(),
)

/**
 * The identifiers new sets and schedules get, chosen once when a file is previewed, so planning again at
 * confirm gives an equal plan unless something here changed.
 */
internal data class ImportIds(
    val sets: Map<String, PauseSetId>,
    val schedules: List<ScheduleId>,
) {
    companion object {
        fun create(
            file: PauseSetFile,
            setIds: PauseSetIdGenerator,
            scheduleIds: ScheduleIdGenerator,
        ): ImportIds {
            return ImportIds(file.sets.associate { set -> set.ref to setIds.create() }, file.schedules.map { scheduleIds.create() })
        }
    }
}

internal enum class ImportRefusal {
    TOO_MANY_SETS,
    TOO_MANY_WEBSITES,
    TOO_MANY_SCHEDULES,

    /** Replace would remove a set or schedule that a running pause uses. */
    IN_USE_BY_PAUSE,
}

internal sealed interface ImportPlanResult {
    data class Planned(
        val plan: ImportPlan,
    ) : ImportPlanResult

    data class Refused(
        val reason: ImportRefusal,
    ) : ImportPlanResult
}

/** What an import writes: the next sets, schedule changes, which sets keep this device's apps, and what to show. */
internal data class ImportPlan(
    val sets: PauseSets,
    val schedulesToSave: List<SchedulePlan>,
    val schedulesToRemove: List<ScheduleId>,
    val keptChoices: Set<PauseSetId>,
    val summary: ImportSummary,
)

/** The preview and result: names as the person sees them. */
internal data class ImportSummary(
    val added: List<String>,
    val combined: List<String>,
    val removed: List<String>,
    val websitesAdded: Int,
    val schedules: List<String>,
    val firstSetName: String?,
    val firstSetAppsStay: Boolean,
    /** Every set the import wrote: the added and combined ones, and on replace every set left. */
    val changed: List<String> = added + combined,
    /** On replace, this device's first-set name when the file's first set takes its place under another name. */
    val firstSetWas: String? = null,
    val removedSchedules: List<String> = emptyList(),
) {
    override fun toString(): String {
        return "ImportSummary(redacted)"
    }
}

/** What replace overwrites: every set left came from the file, and every schedule here goes. */
private fun replaceSummary(
    summary: ImportSummary,
    current: ImportCurrent,
    sets: PauseSets,
): ImportSummary {
    val was = current.sets.sets.firstOrNull { set -> set.id == PauseSetId.FIRST && !set.refused }?.let { set -> displayNameOf(set.name) }
    return summary.copy(
        changed = sets.sets.filterNot(LocalPauseSet::refused).map { set -> displayNameOf(set.name) },
        firstSetWas = was?.takeIf { name -> summary.firstSetAppsStay && name != summary.firstSetName },
        removedSchedules = current.schedules.map(SchedulePlan::name),
    )
}

/** Plans [file] against [current] in [mode]; nothing is written here. */
internal fun planImport(
    current: ImportCurrent,
    file: PauseSetFile,
    mode: ImportMode,
    ids: ImportIds,
): ImportPlanResult {
    val placed = if (mode == ImportMode.REPLACE) replaceSets(current, file, ids) else addSets(current, file, mode, ids)
    val removedSchedules = if (mode == ImportMode.REPLACE) current.schedules.map(SchedulePlan::id) + current.refusedSchedules else emptyList()
    if (mode == ImportMode.REPLACE &&
        (placed.removed.any { set -> set.id in current.runningSets } || removedSchedules.any { id -> id in current.runningSchedules })
    ) {
        return ImportPlanResult.Refused(ImportRefusal.IN_USE_BY_PAUSE)
    }
    val saves = importedSchedules(current, file, mode, ids, placed.mapping)
    val keptSchedules = if (mode == ImportMode.REPLACE) 0 else current.schedules.size
    val refusal = limitRefusal(placed.sets, keptSchedules + saves.size)
    if (refusal != null) {
        return ImportPlanResult.Refused(refusal)
    }
    val sets = PauseSets.of(placed.sets, placed.defaultSetId) ?: return ImportPlanResult.Refused(ImportRefusal.TOO_MANY_WEBSITES)
    val before = current.sets.sets.associateBy(LocalPauseSet::id)
    val firstSet = sets.sets.firstOrNull { set -> set.id == PauseSetId.FIRST && !set.refused }
    val summary = ImportSummary(
        added = sets.sets.filter { set -> set.id !in before }.map { set -> displayNameOf(set.name) },
        combined = placed.combined,
        removed = placed.removed.map { set -> displayNameOf(set.name) },
        websitesAdded = sets.sets.sumOf { set -> (set.domains - before[set.id]?.domains.orEmpty().toSet()).size },
        schedules = saves.map(SchedulePlan::name),
        firstSetName = firstSet?.let { set -> displayNameOf(set.name) },
        firstSetAppsStay = firstSet != null && before[PauseSetId.FIRST]?.refused == false,
    ).let { summary -> if (mode == ImportMode.REPLACE) replaceSummary(summary, current, sets) else summary }
    val kept = sets.sets.map(LocalPauseSet::id).filterTo(mutableSetOf()) { id -> id in before }
    return ImportPlanResult.Planned(ImportPlan(sets, saves, removedSchedules, kept, summary))
}

private class PlacedSets(
    val sets: List<LocalPauseSet>,
    val defaultSetId: PauseSetId?,
    val mapping: Map<String, PauseSetId>,
    val combined: List<String>,
    val removed: List<LocalPauseSet>,
)

private fun addSets(
    current: ImportCurrent,
    file: PauseSetFile,
    mode: ImportMode,
    ids: ImportIds,
): PlacedSets {
    val result = current.sets.sets.toMutableList()
    val taken = result.mapTo(mutableSetOf()) { set -> nameKey(displayNameOf(set.name)) }
    val mapping = mutableMapOf<String, PauseSetId>()
    val combined = mutableListOf<String>()
    val firstLive = result.any { set -> set.id == PauseSetId.FIRST && !set.refused }
    file.sets.forEach { fileSet ->
        val match = when {
            mode != ImportMode.COMBINE -> null

            fileSet.first && firstLive -> PauseSetId.FIRST

            else -> fileSet.name?.let { name ->
                result.firstOrNull { set -> set.id != PauseSetId.FIRST && !set.refused && set.name != null && nameKey(set.name) == nameKey(name) }?.id
            }
        }
        if (match != null) {
            val index = result.indexOfFirst { set -> set.id == match }
            val merged = (result[index].domains + fileSet.domains).distinct()
            if (merged.size > result[index].domains.size) {
                combined += displayNameOf(result[index].name)
            }
            result[index] = result[index].copy(domains = merged)
            mapping[fileSet.ref] = match
        } else {
            val id = checkNotNull(ids.sets[fileSet.ref])
            result += LocalPauseSet(id, uniqueName(fileSet.name ?: UNNAMED_FIRST_SET, taken), fileSet.domains)
            mapping[fileSet.ref] = id
        }
    }
    return PlacedSets(result, current.sets.defaultSetId, mapping, combined, emptyList())
}

private fun replaceSets(
    current: ImportCurrent,
    file: PauseSetFile,
    ids: ImportIds,
): PlacedSets {
    val localFirst = current.sets.sets.firstOrNull { set -> set.id == PauseSetId.FIRST && !set.refused }
    val fileFirst = file.sets.firstOrNull(FileSet::first)?.takeIf { localFirst != null }
    val result = mutableListOf<LocalPauseSet>()
    val mapping = mutableMapOf<String, PauseSetId>()
    if (localFirst != null) {
        result += LocalPauseSet(PauseSetId.FIRST, fileFirst?.name ?: localFirst.name, fileFirst?.domains.orEmpty())
        fileFirst?.let { set -> mapping[set.ref] = PauseSetId.FIRST }
    }
    val taken = result.mapTo(mutableSetOf()) { set -> nameKey(displayNameOf(set.name)) }
    file.sets.filter { set -> set !== fileFirst }.forEach { fileSet ->
        val id = checkNotNull(ids.sets[fileSet.ref])
        result += LocalPauseSet(id, uniqueName(fileSet.name ?: UNNAMED_FIRST_SET, taken), fileSet.domains)
        mapping[fileSet.ref] = id
    }
    val removed = current.sets.sets.filter { set -> set.id != PauseSetId.FIRST || localFirst == null }
    return PlacedSets(result, file.defaultSet?.let(mapping::get), mapping, emptyList(), removed)
}

private fun importedSchedules(
    current: ImportCurrent,
    file: PauseSetFile,
    mode: ImportMode,
    ids: ImportIds,
    mapping: Map<String, PauseSetId>,
): List<SchedulePlan> {
    val known = if (mode == ImportMode.REPLACE) mutableListOf() else current.schedules.toMutableList()
    val saves = mutableListOf<SchedulePlan>()
    file.schedules.forEachIndexed { index, schedule ->
        val plan = SchedulePlan(
            id = ids.schedules[index],
            name = schedule.name,
            weekdays = schedule.weekdays,
            startMinute = schedule.startMinute,
            endMinute = schedule.endMinute,
            enabled = false,
            setId = checkNotNull(mapping[schedule.set]),
        )
        // One rule for a schedule already here and one repeated in the file, so a repeat never costs a place.
        if (known.none { existing -> existing.sameAs(plan) }) {
            known += plan
            saves += plan
        }
    }
    return saves
}

private fun limitRefusal(
    sets: List<LocalPauseSet>,
    scheduleCount: Int,
): ImportRefusal? {
    return when {
        sets.count { set -> !set.refused } > SyncFormatLimits.MAX_PAUSE_SETS -> ImportRefusal.TOO_MANY_SETS

        sets.flatMapTo(
            mutableSetOf<ExactDomain>(),
            LocalPauseSet::domains,
        ).size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT -> ImportRefusal.TOO_MANY_WEBSITES

        scheduleCount > ScheduleLimits.MAX_SCHEDULES -> ImportRefusal.TOO_MANY_SCHEDULES

        else -> null
    }
}

/** Two schedules are the same plan when everything but the identifier and the switch matches. */
private fun SchedulePlan.sameAs(other: SchedulePlan): Boolean {
    return nameKey(name) == nameKey(other.name) &&
        weekdays == other.weekdays &&
        startMinute == other.startMinute &&
        endMinute == other.endMinute &&
        setId == other.setId
}

/** [base], or "[base] (imported)", then "(imported 2)" and on, within a set name's 80 bytes; the result is taken. */
private fun uniqueName(
    base: String,
    taken: MutableSet<String>,
): String {
    var attempt = 1
    var name = base
    while (nameKey(name) in taken) {
        val suffix = if (attempt == 1) IMPORTED_SUFFIX else "$IMPORTED_SUFFIX_OPEN $attempt)"
        name = withinNameBytes(base, suffix) + suffix
        attempt++
    }
    taken += nameKey(name)
    return name
}

private fun withinNameBytes(
    base: String,
    suffix: String,
): String {
    var trimmed = base
    while ((trimmed + suffix).encodeToByteArray().size > ScheduleLimits.MAX_NAME_BYTES) {
        // A whole character at a time, so an emoji is never cut in half.
        trimmed = trimmed.dropLast(if (trimmed.length >= 2 && trimmed.last().isLowSurrogate()) 2 else 1)
    }
    return trimmed.trimEnd()
}

private fun nameKey(name: String): String {
    return name.trim().lowercase()
}

/** The stored name an unnamed first set takes when it becomes a separate set. */
private const val UNNAMED_FIRST_SET: String = "My set"
private const val IMPORTED_SUFFIX: String = " (imported)"
private const val IMPORTED_SUFFIX_OPEN: String = " (imported"
