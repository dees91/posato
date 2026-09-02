package app.posato.feature.sync.domain

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.canonicalDigest
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ExactDomain
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncReducerTest {
    @Test
    fun `given a sequence gap when reduced then later operations remain unapplied until the gap arrives`() {
        val domain = checkNotNull(ExactDomain.restore("gap.example"))
        val registration = testOperation(1, 1, SyncOperationPayload.AuthorRegister)
        val third = testOperation(3, 3, SyncOperationPayload.DomainPresent(domain))

        val beforeGap = SyncReducer.reduce(listOf(registration, third))
        val afterGap = SyncReducer.reduce(
            listOf(registration, third, testOperation(2, 2, SyncOperationPayload.DomainAbsent(domain))),
        )

        assertEquals(emptyList(), beforeGap.domains)
        assertEquals(SyncAuditOutcome.SEQUENCE_GAP, beforeGap.audit.single { entry -> entry.operationId == third.operationId }.outcome)
        assertEquals(listOf(domain), afterGap.domains)
    }

    @Test
    fun `given more than two thousand forty eight domains when reduced then capacity is deterministic`() {
        val operations = mutableListOf(testOperation(1, 1, SyncOperationPayload.AuthorRegister))
        repeat(SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS + 1) { index ->
            val domain = checkNotNull(ExactDomain.restore("d$index.example"))
            operations += testOperation(index + 2, index.toLong() + 2, SyncOperationPayload.DomainPresent(domain))
        }

        val forward = SyncReducer.reduce(operations)
        val reverse = SyncReducer.reduce(operations.reversed())

        assertEquals(SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS, forward.domains.size)
        assertEquals(forward, reverse)
        assertEquals(1, forward.audit.count { entry -> entry.outcome == SyncAuditOutcome.DOMAIN_CAPACITY })
    }

    @Test
    fun `given conflicting session starts when evaluated then the session is quarantined in every delivery order`() {
        val sessionId = SessionId(testIdentifier(80))
        val operations = listOf(
            testOperation(1, 1, SyncOperationPayload.AuthorRegister),
            testOperation(2, 2, SyncOperationPayload.SessionStart(sessionId, 100, 200)),
            testOperation(3, 3, SyncOperationPayload.SessionStart(sessionId, 100, 250)),
        )

        val projection = SyncReducer.reduce(operations.reversed())

        assertTrue(sessionId in projection.conflictedSessionIds)
        assertIs<EffectiveSession.Inactive>(SyncReducer.evaluateSession(projection, 150, emptySet()))
        assertEquals(2, projection.audit.count { entry -> entry.outcome == SyncAuditOutcome.SESSION_CONFLICT })
    }

    @Test
    fun `given an active session when a terminal expiry fact exists then rollback cannot reactivate it`() {
        val sessionId = SessionId(testIdentifier(81))
        val projection = SyncReducer.reduce(
            listOf(
                testOperation(1, 1, SyncOperationPayload.AuthorRegister),
                testOperation(2, 2, SyncOperationPayload.SessionStart(sessionId, 100, 200)),
            ),
        )

        assertIs<EffectiveSession.Active>(SyncReducer.evaluateSession(projection, 150, emptySet()))
        assertIs<EffectiveSession.Inactive>(SyncReducer.evaluateSession(projection, 150, setOf(sessionId)))
    }

    @Test
    fun `given reordered duplicate operations when reduced then state audit and canonical digest converge`() {
        val registration = testOperation(1, 1, SyncOperationPayload.AuthorRegister)
        val present = testOperation(
            2,
            2,
            SyncOperationPayload.DomainPresent(checkNotNull(ExactDomain.restore("stable.example"))),
        )
        val absent = testOperation(
            3,
            3,
            SyncOperationPayload.DomainAbsent(checkNotNull(ExactDomain.restore("stable.example"))),
        )
        val provider = FakeSyncCryptoProvider()

        val first = SyncReducer.reduce(listOf(registration, present, absent))
        val second = SyncReducer.reduce(listOf(absent, present, registration, present, absent))

        assertEquals(first, second)
        assertEquals(first.canonicalDigest(provider), second.canonicalDigest(provider))
    }

    @Test
    fun `given removal frees domain capacity when reduced then the previously rejected domain is not retroactively applied`() {
        val retained = (0 until SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS).map { index ->
            checkNotNull(ExactDomain.restore("retained-$index.example"))
        }
        val rejected = checkNotNull(ExactDomain.restore("rejected.example"))
        val operations = mutableListOf(testOperation(1, 1, SyncOperationPayload.AuthorRegister))
        retained.forEachIndexed { index, domain ->
            operations += testOperation(index + 2, index.toLong() + 2, SyncOperationPayload.DomainPresent(domain))
        }
        operations += testOperation(
            operations.size + 1,
            operations.size.toLong() + 1,
            SyncOperationPayload.DomainPresent(rejected),
        )
        operations += testOperation(
            operations.size + 1,
            operations.size.toLong() + 1,
            SyncOperationPayload.DomainAbsent(retained.first()),
        )

        val projection = SyncReducer.reduce(operations)

        assertEquals(SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS - 1, projection.domains.size)
        assertTrue(rejected !in projection.domains)
        assertEquals(1, projection.audit.count { it.outcome == SyncAuditOutcome.DOMAIN_CAPACITY })
    }

    @Test
    fun `given ended future and older sessions when evaluated then there is no fallback to an older session`() {
        val older = SessionId(testIdentifier(91))
        val newer = SessionId(testIdentifier(92))
        val projection = SyncReducer.reduce(
            listOf(
                testOperation(1, 1, SyncOperationPayload.AuthorRegister),
                testOperation(2, 2, SyncOperationPayload.SessionStart(older, 100, 500)),
                testOperation(3, 3, SyncOperationPayload.SessionStart(newer, 200, 400)),
                testOperation(4, 4, SyncOperationPayload.SessionEnd(newer)),
            ),
        )

        assertIs<EffectiveSession.Inactive>(SyncReducer.evaluateSession(projection, 50, emptySet()))
        assertIs<EffectiveSession.Active>(SyncReducer.evaluateSession(projection, 150, emptySet()))
        assertIs<EffectiveSession.Inactive>(SyncReducer.evaluateSession(projection, 250, emptySet()))
        assertIs<EffectiveSession.Inactive>(SyncReducer.evaluateSession(projection, 450, emptySet()))
    }

    @Test
    fun `given seeded delivery permutations and duplicates when reduced then projection and digest always converge`() {
        val first = checkNotNull(ExactDomain.restore("first.example"))
        val second = checkNotNull(ExactDomain.restore("second.example"))
        val operations = listOf(
            testOperation(1, 1, SyncOperationPayload.AuthorRegister),
            testOperation(2, 2, SyncOperationPayload.DomainPresent(first)),
            testOperation(3, 3, SyncOperationPayload.DomainPresent(second)),
            testOperation(4, 4, SyncOperationPayload.DomainAbsent(first)),
            testOperation(5, 5, SyncOperationPayload.ApplicationPolicyAbsent),
        )
        val provider = FakeSyncCryptoProvider()
        val expected = SyncReducer.reduce(operations)
        val expectedDigest = expected.canonicalDigest(provider)

        repeat(64) { seed ->
            val delivered = (operations + operations[1] + operations[3]).shuffled(Random(seed))
            val actual = SyncReducer.reduce(delivered)

            assertEquals(expected, actual, "seed=$seed")
            assertEquals(expectedDigest, actual.canonicalDigest(provider), "seed=$seed")
        }
    }
}
