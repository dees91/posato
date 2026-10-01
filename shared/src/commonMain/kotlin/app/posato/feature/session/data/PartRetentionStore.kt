package app.posato.feature.session.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.data.KeptApplication
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What one running part has paused on this device, kept until the part ends. */
internal class RetainedItems(
    val domains: Set<String> = emptySet(),
    val applications: List<KeptApplication> = emptyList(),
) {
    override fun toString(): String {
        return "RetainedItems(redacted)"
    }
}

/**
 * Reads and grows each running part's retention. Holding writes only what is new and, in the same
 * transaction, drops rows of parts that are no longer running, so a part that ended meanwhile keeps nothing.
 */
internal interface PartRetentionStore {
    suspend fun read(part: RetainedPart): RetainedItems

    suspend fun hold(
        part: RetainedPart,
        items: RetainedItems,
    )
}

@Inject
@SingleIn(AppScope::class)
internal class SqlPartRetentionStore(
    private val database: PosatoDatabase,
    @Named("database") private val dispatcher: CoroutineDispatcher,
) : PartRetentionStore {
    override suspend fun read(part: RetainedPart): RetainedItems {
        return withContext(dispatcher) {
            val queries = database.localSessionQueries
            val domains = queries.selectRetainedDomains(part.kind, part.id, part.year, part.month, part.day).awaitAsList()
            val applications = queries.selectRetainedApplications(part.kind, part.id, part.year, part.month, part.day).awaitAsList()
                .map { row -> KeptApplication(row.mapping_id, row.display_name, row.designated_requirement) }
            RetainedItems(domains.toSet(), applications)
        }
    }

    override suspend fun hold(
        part: RetainedPart,
        items: RetainedItems,
    ) {
        withContext(dispatcher) {
            database.transaction {
                database.retain(part, items.domains.toList(), items.applications)
                database.sweepRetention()
            }
        }
    }
}
