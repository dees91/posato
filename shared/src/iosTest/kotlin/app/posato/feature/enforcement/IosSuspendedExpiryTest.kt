package app.posato.feature.enforcement

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosSuspendedExpiryTest {
    @Test
    fun `given a schedule when completed then every provider outcome is preserved and the request is forwarded`() = runTest {
        IosSuspendedExpiryOutcome.entries.forEach { outcome ->
            val provider = FakeIosSuspendedExpiryProvider(scheduleOutcome = outcome)
            val request = IosSuspendedExpiryRequest("session", 1_700_000_000L, 1_700_003_600L)

            assertEquals(outcome, IosSuspendedExpiry(provider).schedule(request), "outcome $outcome")
            assertEquals(request, provider.seenRequest, "outcome $outcome")
        }
    }

    @Test
    fun `given a cancel when completed then the provider outcome is preserved`() = runTest {
        IosSuspendedExpiryOutcome.entries.forEach { outcome ->
            val provider = FakeIosSuspendedExpiryProvider(cancelOutcome = outcome)

            assertEquals(outcome, IosSuspendedExpiry(provider).cancel(), "outcome $outcome")
            assertTrue(provider.cancelCalled, "outcome $outcome")
        }
    }

    @Test
    fun `given a reconciliation read when completed then every state is preserved and the session is forwarded`() = runTest {
        IosExpiryReconciliation.entries.forEach { reconciliation ->
            val provider = FakeIosSuspendedExpiryProvider(reconciliation = reconciliation)

            assertEquals(reconciliation, IosSuspendedExpiry(provider).readReconciliation("session"), "state $reconciliation")
            assertEquals("session", provider.seenSessionId, "state $reconciliation")
            assertTrue(provider.reconciliationCalled, "state $reconciliation")
        }
    }

    @Test
    fun `given an acknowledgement when completed then the session is forwarded`() = runTest {
        val provider = FakeIosSuspendedExpiryProvider()

        assertTrue(IosSuspendedExpiry(provider).acknowledgeReconciliation("session"))
        assertEquals(listOf("session"), provider.acknowledgedSessionIds)
    }

    @Test
    fun `given a displacement read when completed then the foreign signal is forwarded unconsumed`() = runTest {
        val provider = FakeIosSuspendedExpiryProvider(displacedSessionId = "earlier-session")

        assertEquals("earlier-session", IosSuspendedExpiry(provider).displacedClearedSessionId("session").sessionId)
        assertEquals("session", provider.seenDisplacedCurrentId)
        assertTrue(provider.acknowledgedSessionIds.isEmpty())
    }

    @Test
    fun `given no foreign signal when displacing then absent is preserved`() = runTest {
        val provider = FakeIosSuspendedExpiryProvider(displacedSessionId = null)

        assertNull(IosSuspendedExpiry(provider).displacedClearedSessionId("session").sessionId)
        assertEquals("session", provider.seenDisplacedCurrentId)
    }

    @Test
    fun `given a failed displacement read then failure stays distinct from absence`() = runTest {
        val provider = FakeIosSuspendedExpiryProvider(displacementFailure = true)
        val result = IosSuspendedExpiry(provider).displacedClearedSessionId("session")
        assertEquals(ExpiryDisplacementOutcome.FAILED, result.outcome)
        assertNull(result.sessionId)
    }

    @Test
    fun `given the new expiry carrier when described then values stay redacted`() {
        assertEquals(
            "IosSuspendedExpiryRequest(redacted)",
            IosSuspendedExpiryRequest("session", 1_700_000_000L, 1_700_003_600L).toString(),
        )
    }
}

private class FakeIosSuspendedExpiryProvider(
    private val scheduleOutcome: IosSuspendedExpiryOutcome = IosSuspendedExpiryOutcome.SCHEDULED,
    private val cancelOutcome: IosSuspendedExpiryOutcome = IosSuspendedExpiryOutcome.CANCELLED,
    private val reconciliation: IosExpiryReconciliation = IosExpiryReconciliation.UNKNOWN,
    private val displacedSessionId: String? = null,
    private val displacementFailure: Boolean = false,
) : IosSuspendedExpiryProvider {
    var cancelCalled = false
        private set
    var reconciliationCalled = false
        private set
    var seenRequest: IosSuspendedExpiryRequest? = null
        private set
    var seenSessionId: String? = null
        private set

    override fun schedule(
        request: IosSuspendedExpiryRequest,
        completion: (IosSuspendedExpiryOutcome) -> Unit,
    ) {
        seenRequest = request
        completion(scheduleOutcome)
    }

    override fun cancel(handler: (IosSuspendedExpiryOutcome) -> Unit) {
        cancelCalled = true
        handler(cancelOutcome)
    }

    override fun readReconciliation(
        sessionId: String,
        handler: (IosExpiryReconciliation) -> Unit,
    ) {
        reconciliationCalled = true
        seenSessionId = sessionId
        handler(reconciliation)
    }

    override fun acknowledgeReconciliation(
        sessionId: String,
        handler: (Boolean) -> Unit,
    ) {
        acknowledgedSessionIds += sessionId
        handler(true)
    }

    override fun displacedClearedSessionId(
        currentSessionId: String,
        handler: (ExpiryDisplacement) -> Unit,
    ) {
        seenDisplacedCurrentId = currentSessionId
        handler(
            ExpiryDisplacement(
                if (displacementFailure) {
                    ExpiryDisplacementOutcome.FAILED
                } else if (displacedSessionId ==
                    null
                ) {
                    ExpiryDisplacementOutcome.ABSENT
                } else {
                    ExpiryDisplacementOutcome.PRESENT
                },
                displacedSessionId,
            ),
        )
    }

    val acknowledgedSessionIds = mutableListOf<String>()
    var seenDisplacedCurrentId: String? = null
        private set
}
