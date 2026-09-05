package app.posato.feature.enforcement

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `given a reconciliation read when completed then every state is preserved`() = runTest {
        IosExpiryReconciliation.entries.forEach { reconciliation ->
            val provider = FakeIosSuspendedExpiryProvider(reconciliation = reconciliation)

            assertEquals(reconciliation, IosSuspendedExpiry(provider).readReconciliation(), "state $reconciliation")
            assertTrue(provider.reconciliationCalled, "state $reconciliation")
        }
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
) : IosSuspendedExpiryProvider {
    var cancelCalled = false
        private set
    var reconciliationCalled = false
        private set
    var seenRequest: IosSuspendedExpiryRequest? = null
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

    override fun readReconciliation(handler: (IosExpiryReconciliation) -> Unit) {
        reconciliationCalled = true
        handler(reconciliation)
    }
}
