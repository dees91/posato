package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.data.PART_OCCURRENCE
import app.posato.feature.session.data.PART_SESSION
import app.posato.feature.session.data.RetainedPart
import app.posato.feature.session.data.retain
import app.posato.feature.session.data.sweepRetention
import app.posato.feature.session.domain.SessionClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A Mac app choice a running part keeps: its mapping identifier, display name and designated requirement. */
public class KeptApplication(
    internal val mappingId: ByteArray,
    internal val displayName: ByteArray,
    internal val designatedRequirement: ByteArray,
) {
    override fun toString(): String {
        return "KeptApplication(redacted)"
    }
}

/**
 * The step migration 13 leaves pending. A manual session or schedule occurrence running at that moment keeps
 * the first set's current websites and this device's kept apps (`user-confirmed` 2026-09-30: current items
 * only), so the upgrade itself releases nothing those parts enforce now. It runs once, before any enforcement
 * or edit, and a failure leaves it pending for the next launch.
 */
internal class PauseSetUpgrade(
    private val database: PosatoDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
    private val keptApplications: suspend () -> List<KeptApplication>,
) {
    suspend fun complete() {
        val pending = withContext(dispatcher) { database.localExactDomainPolicyQueries.selectUpgradePending().awaitAsList().isNotEmpty() }
        if (!pending) {
            return
        }
        val applications = keptApplications()
        withContext(dispatcher) {
            database.transaction {
                val now = clock()
                val domains = database.localExactDomainPolicyQueries.selectSetDomains(Long.MAX_VALUE).awaitAsList()
                    .filter { row -> row.set_id.all { byte -> byte == 0.toByte() } }
                    .map { row -> row.canonical_domain }
                runningParts(now).forEach { part -> database.retain(part, domains, applications) }
                database.localSessionQueries.clearFrozenStartSet()
                database.sweepRetention()
                database.localExactDomainPolicyQueries.deleteUpgradePending()
            }
        }
    }

    private suspend fun runningParts(now: Long): List<RetainedPart> {
        val session = database.localSessionQueries.selectSession().awaitAsList().singleOrNull()
            ?.takeIf { row -> row.ended_early == 0L && row.start_epoch_millis <= now && now < row.end_epoch_millis }
            ?.takeIf { row -> database.localSessionQueries.selectExpiryMarker(row.session_id).awaitAsList().isEmpty() }
            ?.let { row -> RetainedPart(PART_SESSION, row.session_id) }
        val occurrences = database.scheduleQueries.selectPinsRunningAt(now).awaitAsList().map { pin ->
            RetainedPart(PART_OCCURRENCE, pin.schedule_id, pin.year, pin.month, pin.day)
        }
        return listOfNotNull(session) + occurrences
    }
}

/**
 * What every launch does before any enforcement or edit: finishes the one-time step migration 13 left, and
 * deletes the app choices of sets this device no longer has, such as one another device removed.
 */
@Inject
@SingleIn(AppScope::class)
public class PauseSetPreparation internal constructor(
    private val database: PosatoDatabase,
    @Named("database") private val dispatcher: CoroutineDispatcher,
    private val clock: SessionClock,
    private val policies: LocalTargetPolicyStore,
    private val mappings: LocalApplicationMappings,
) {
    public suspend fun prepare(keptApplications: suspend () -> List<KeptApplication>) {
        PauseSetUpgrade(database, dispatcher, clock::currentEpochMillis, keptApplications).complete()
        val sets = (policies.read() as? LocalPolicyResult.Success)?.value?.sets?.sets ?: return
        mappings.retainSets(sets.mapTo(mutableSetOf()) { set -> set.id })
    }
}

/** Keeps this device's app choices for [sets] only, such as after another device removed a set. */
internal suspend fun LocalApplicationMappings.retainSets(sets: Set<app.posato.feature.sync.domain.PauseSetId>) {
    retainOnly(sets.mapNotNullTo(mutableSetOf()) { id -> ApplicationChoiceSet.restore(id.hex()) })
}

private fun app.posato.feature.sync.domain.PauseSetId.hex(): String {
    return value.copyBytes().joinToString("") { byte -> (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
}

private const val BYTE_MASK: Int = 0xFF
private const val HEX_RADIX: Int = 16
