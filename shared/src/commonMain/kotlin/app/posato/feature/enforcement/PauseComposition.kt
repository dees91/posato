package app.posato.feature.enforcement

/** Websites (canonical hosts) and app choice identifiers that a pause holds or a set resolves to. */
internal data class PauseItems(
    val domains: Set<String> = emptySet(),
    val appIds: Set<String> = emptySet(),
) {
    operator fun plus(other: PauseItems): PauseItems {
        return PauseItems(domains + other.domains, appIds + other.appIds)
    }

    override fun toString(): String {
        return "PauseItems(redacted)"
    }
}

/**
 * One running part: the manual session or a schedule occurrence. [current] is what its set resolves to now;
 * [retained] is what it has already paused on this device, which it keeps until it ends.
 */
internal data class RunningPartItems(
    val partId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val current: PauseItems,
    val retained: PauseItems,
) {
    override fun toString(): String {
        return "RunningPartItems(redacted)"
    }
}

/** How many websites and apps a device can pause at once; [domainCost] counts what the platform applies. */
internal class PauseLimits(
    val maxDomainCost: Int,
    val maxApps: Int,
    val domainCost: (Set<String>) -> Int,
) {
    companion object {
        val MAC: PauseLimits = PauseLimits(MAC_DOMAINS, MAC_APPS) { domains -> domains.size }

        /** iPhone and iPad: the web filter counts each host with its `www` counterpart, over every store. */
        val IPHONE: PauseLimits = PauseLimits(IPHONE_WEB_DOMAINS, IPHONE_APPS) { domains -> webDomainsOf(domains).size }
    }
}

/** What to apply for the running parts, what each part now holds, and how many of its items wait for room. */
internal data class PausePlan(
    val items: PauseItems,
    val held: Map<String, PauseItems>,
    val deferred: Map<String, Int>,
    val partId: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
) {
    override fun toString(): String {
        return "PausePlan(redacted)"
    }
}

/**
 * Composes the running parts into one pause. What any part already holds stays and always fits. Then, from
 * the earliest part on, a part that holds nothing yet (it just started) adds its items in alphabetical order
 * while they fit, and a running part's additions (an edit to its set) are added only when all of them fit;
 * the rest wait and are tried again at the next composition. [occupied] is what another store on the device
 * already pauses, which counts toward the limits but is not applied here.
 */
internal fun planPause(
    parts: List<RunningPartItems>,
    limits: PauseLimits,
    occupied: PauseItems = PauseItems(),
): PausePlan {
    val ordered = parts.sortedWith(compareBy(RunningPartItems::startEpochMillis, RunningPartItems::partId))
    var held = ordered.fold(PauseItems()) { union, part -> union + part.retained }
    val deferred = mutableMapOf<String, Int>()
    ordered.forEach { part ->
        val joining = part.retained.domains.isEmpty() && part.retained.appIds.isEmpty()
        val domains = admit(part.current.domains - held.domains, joining) { added ->
            limits.domainCost(held.domains + occupied.domains + added) <= limits.maxDomainCost
        }
        val apps = admit(part.current.appIds - held.appIds, joining) { added ->
            (held.appIds + occupied.appIds + added).size <= limits.maxApps
        }
        held += PauseItems(domains.admitted, apps.admitted)
        deferred[part.partId] = domains.waiting + apps.waiting
    }
    val heldByPart = ordered.associate { part ->
        part.partId to PauseItems(
            part.retained.domains + (part.current.domains intersect held.domains),
            part.retained.appIds + (part.current.appIds intersect held.appIds),
        )
    }
    val first = ordered.firstOrNull()
    return PausePlan(
        items = held,
        held = heldByPart,
        deferred = deferred,
        partId = first?.partId,
        startEpochMillis = first?.startEpochMillis ?: 0,
        endEpochMillis = ordered.maxOfOrNull(RunningPartItems::endEpochMillis) ?: 0,
    )
}

private class Admission(
    val admitted: Set<String>,
    val waiting: Int,
)

/** A joining part takes what fits in alphabetical order; an edit's additions come all together or not at all. */
private fun admit(
    candidates: Set<String>,
    joining: Boolean,
    fits: (Set<String>) -> Boolean,
): Admission {
    if (candidates.isEmpty()) {
        return Admission(emptySet(), 0)
    }
    if (!joining) {
        return if (fits(candidates)) Admission(candidates, 0) else Admission(emptySet(), candidates.size)
    }
    val admitted = linkedSetOf<String>()
    candidates.sorted().forEach { candidate -> if (fits(admitted + candidate)) admitted += candidate }
    return Admission(admitted, candidates.size - admitted.size)
}

/** The web domains the platform blocks for [hosts]: each host and its `www` counterpart, as iOS applies them. */
internal fun webDomainsOf(hosts: Set<String>): Set<String> {
    return hosts.flatMapTo(mutableSetOf()) { host -> listOfNotNull(host, wwwCounterpart(host)) }
}

private fun wwwCounterpart(host: String): String? {
    val labels = host.split('.')
    return when {
        labels.size < MIN_HOST_LABELS -> null
        labels.first() == "www" -> if (labels.size > MIN_HOST_LABELS) labels.drop(1).joinToString(".") else null
        else -> "www.$host"
    }
}

private const val MIN_HOST_LABELS: Int = 2
private const val MAC_DOMAINS: Int = 1_024
private const val MAC_APPS: Int = 64
private const val IPHONE_WEB_DOMAINS: Int = 50
private const val IPHONE_APPS: Int = 50
