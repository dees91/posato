package app.posato.feature.targets.domain

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.sync.domain.SyncFormatLimits

/** One pause set on this device: its synchronized name (absent for an unnamed first set) and its websites. */
internal data class LocalPauseSet(
    val id: PauseSetId,
    val name: String?,
    val domains: List<ExactDomain>,
    val refused: Boolean = false,
) {
    override fun toString(): String {
        return "LocalPauseSet(redacted)"
    }
}

/**
 * Every pause set this device keeps. At most ten are live (refused sets wait for a free slot), and the
 * websites of all sets together hold at most [ExactDomainPolicyLimits.MAX_DOMAIN_COUNT] unique domains: an
 * address in three sets counts once.
 */
internal class PauseSets private constructor(
    val sets: List<LocalPauseSet>,
    val defaultSetId: PauseSetId?,
) {
    fun domainsOf(setId: PauseSetId): List<ExactDomain> {
        return sets.firstOrNull { set -> set.id == setId }?.domains.orEmpty()
    }

    /**
     * The set a new pause uses unless another is chosen: the stored default while it is live here, else the
     * first set, else any live set. Null when this device has no live set.
     */
    fun resolvedDefault(): PauseSetId? {
        val live = sets.filter { set -> !set.refused }
        return live.firstOrNull { set -> set.id == defaultSetId }?.id
            ?: live.firstOrNull { set -> set.id == PauseSetId.FIRST }?.id
            ?: live.firstOrNull()?.id
    }

    fun uniqueDomains(): Set<ExactDomain> {
        return sets.flatMapTo(linkedSetOf(), LocalPauseSet::domains)
    }

    /** The same sets with [setId]'s websites replaced; null when the result breaks a limit or the set is missing. */
    fun withDomains(
        setId: PauseSetId,
        domains: List<ExactDomain>,
    ): PauseSets? {
        if (sets.none { set -> set.id == setId }) {
            return null
        }
        return of(sets.map { set -> if (set.id == setId) set.copy(domains = domains) else set }, defaultSetId)
    }

    override fun equals(other: Any?): Boolean {
        return other is PauseSets && sets == other.sets && defaultSetId == other.defaultSetId
    }

    override fun hashCode(): Int {
        return 31 * sets.hashCode() + defaultSetId.hashCode()
    }

    override fun toString(): String {
        return "PauseSets(redacted)"
    }

    companion object {
        fun of(
            sets: List<LocalPauseSet>,
            defaultSetId: PauseSetId?,
        ): PauseSets? {
            val valid = sets.distinctBy(LocalPauseSet::id).size == sets.size &&
                sets.count { set -> !set.refused } <= SyncFormatLimits.MAX_PAUSE_SETS &&
                sets.all { set -> set.domains.distinct().size == set.domains.size } &&
                sets.all { set -> set.name == null || ScheduleWireRules.isValidName(set.name) } &&
                sets.flatMapTo(mutableSetOf(), LocalPauseSet::domains).size <= ExactDomainPolicyLimits.MAX_DOMAIN_COUNT
            return if (valid) PauseSets(sets.sortedBy { set -> set.id.value }, defaultSetId) else null
        }

        /** What a device holds before anyone creates a second set: the unnamed first set with [domains]. */
        fun firstSetOnly(domains: List<ExactDomain>): PauseSets {
            return checkNotNull(of(listOf(LocalPauseSet(PauseSetId.FIRST, null, domains)), null))
        }
    }
}
