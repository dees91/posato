package app.posato.feature.sync.domain

import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.WebsiteWindow
import kotlin.test.Test
import kotlin.test.assertEquals

/** Kind 22's reduction (ADR 0006 TARGETS-013 amendment); every expectation comes from its text. */
class SyncReducerWindowTest {
    private val register = testOperation(1, 1, SyncOperationPayload.AuthorRegister)
    private val workSet = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val news = checkNotNull(ExactDomain.restore("news.example"))
    private val hourly = checkNotNull(WebsiteWindow.of(60, 5))
    private val halfHourly = checkNotNull(WebsiteWindow.of(30, 15))

    private fun ops(vararg payloads: SyncOperationPayload): List<SyncOperation> {
        return listOf(register) + payloads.mapIndexed { index, payload -> testOperation(index + 2, index.toLong() + 2, payload) }
    }

    private fun SyncProjection.windowOf(
        setId: PauseSetId,
        domain: ExactDomain = news,
    ): WebsiteWindow? {
        return pauseSets.single { set -> set.setId == setId }.windows[domain]
    }

    private fun window(
        window: WebsiteWindow?,
        setId: PauseSetId = workSet,
    ): SyncOperationPayload {
        return SyncOperationPayload.SetDomainWindow(setId, news, window)
    }

    @Test
    fun `given rules for a present website when reduced in any arrival order then the greatest total order wins`() {
        val operations = ops(
            SyncOperationPayload.PauseSetPut(workSet, "Work"),
            SyncOperationPayload.DomainPresent(news, workSet),
            window(hourly),
            window(halfHourly),
        )

        listOf(operations, operations.reversed()).forEach { order ->
            assertEquals(halfHourly, SyncReducer.reduce(order).windowOf(workSet))
        }
    }

    @Test
    fun `given a none pair as the winner when reduced then the website has no rule`() {
        val projection = SyncReducer.reduce(
            ops(SyncOperationPayload.PauseSetPut(workSet, "Work"), SyncOperationPayload.DomainPresent(news, workSet), window(hourly), window(null)),
        )

        assertEquals(null, projection.windowOf(workSet))
    }

    @Test
    fun `given a rule ordered before a removal of its website when the website is added again then it has no rule`() {
        val operations = ops(
            SyncOperationPayload.PauseSetPut(workSet, "Work"),
            SyncOperationPayload.DomainPresent(news, workSet),
            window(hourly),
            SyncOperationPayload.DomainAbsent(news, workSet),
            SyncOperationPayload.DomainPresent(news, workSet),
        )

        listOf(operations, operations.reversed()).forEach { order ->
            assertEquals(null, SyncReducer.reduce(order).windowOf(workSet))
        }
    }

    @Test
    fun `given a rule ordered after a removal and the add again when reduced then it applies`() {
        val projection = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(workSet, "Work"),
                SyncOperationPayload.DomainPresent(news, workSet),
                window(hourly),
                SyncOperationPayload.DomainAbsent(news, workSet),
                window(halfHourly),
                SyncOperationPayload.DomainPresent(news, workSet),
            ),
        )

        assertEquals(halfHourly, projection.windowOf(workSet))
    }

    @Test
    fun `given a kind 3 removal for the first set when reduced then it clears that set's rule like kind 15`() {
        val projection = SyncReducer.reduce(
            ops(
                SyncOperationPayload.DomainPresent(news),
                window(hourly, PauseSetId.FIRST),
                SyncOperationPayload.DomainAbsent(news),
                SyncOperationPayload.DomainPresent(news),
            ),
        )

        assertEquals(null, projection.windowOf(PauseSetId.FIRST))
    }

    @Test
    fun `given a rule for an absent website or a removed set when reduced then nothing projects, and a later add shows the rule`() {
        val absent = SyncReducer.reduce(ops(SyncOperationPayload.PauseSetPut(workSet, "Work"), window(hourly)))
        val added = SyncReducer.reduce(
            ops(SyncOperationPayload.PauseSetPut(workSet, "Work"), window(hourly), SyncOperationPayload.DomainPresent(news, workSet)),
        )
        val removedSet = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(workSet, "Work"),
                SyncOperationPayload.DomainPresent(news, workSet),
                window(hourly),
                SyncOperationPayload.PauseSetRemove(workSet),
            ),
        )

        assertEquals(emptyMap(), absent.pauseSets.single { it.setId == workSet }.windows)
        assertEquals(hourly, added.windowOf(workSet), "kind 14 resets nothing")
        assertEquals(emptyList(), removedSet.pauseSets.filter { it.setId == workSet })
    }

    @Test
    fun `given the same website in two sets when only one is ruled then the other set has no rule`() {
        val projection = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(workSet, "Work"),
                SyncOperationPayload.DomainPresent(news, workSet),
                SyncOperationPayload.DomainPresent(news, PauseSetId.FIRST),
                window(hourly),
            ),
        )

        assertEquals(hourly, projection.windowOf(workSet))
        assertEquals(null, projection.windowOf(PauseSetId.FIRST))
    }

    @Test
    fun `given a rule when reduced then its audit outcome is applied and a domain is neither added nor removed`() {
        val operations = ops(SyncOperationPayload.PauseSetPut(workSet, "Work"), window(hourly))
        val projection = SyncReducer.reduce(operations)

        assertEquals(SyncAuditOutcome.APPLIED, projection.audit.single { it.operationId == operations.last().operationId }.outcome)
        assertEquals(emptyList(), projection.pauseSetDomains(workSet))
    }
}
