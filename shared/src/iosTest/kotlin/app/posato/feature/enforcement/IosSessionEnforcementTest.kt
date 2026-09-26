package app.posato.feature.enforcement

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosSessionEnforcementTest {
    @Test
    fun `given an empty request when applying then nothing is enforced without touching the providers`() = runTest {
        val providers = recordingProviders()
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        val report = enforcement.apply(request(domains = emptyList(), mappingIds = emptyList()))

        assertEquals(EnforcementOutcome.NOTHING_TO_ENFORCE, report.outcome)
        assertTrue(providers.calls.isEmpty())
    }

    @Test
    fun `given healthy providers when applying then restrictions apply and the interval is scheduled`() = runTest {
        val providers = recordingProviders()
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        val report = enforcement.apply(request())

        assertEquals(EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false), report)
        assertEquals(listOf("apply", "schedule"), providers.calls)
        assertEquals("session", providers.scheduling.seenRequest?.sessionId)
    }

    @Test
    fun `given a scheduled expiry for the session when asked whether it is held then the session is held`() = runTest {
        val providers = recordingProviders()
        providers.scheduling.scheduledSessionIds = setOf("session")
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        assertTrue(enforcement.holdsSession("session"))
        assertFalse(enforcement.holdsSession("other-session"))
        assertEquals(listOf("isScheduled", "isScheduled"), providers.calls)
    }

    @Test
    fun `given a short session when applying then the active outcome names the platform limit`() = runTest {
        val providers = recordingProviders(scheduleOutcome = IosSuspendedExpiryOutcome.BELOW_PLATFORM_MINIMUM)
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        val report = enforcement.apply(request())

        assertEquals(EnforcementApplyReport(EnforcementOutcome.APPLIED, true, false), report)
    }

    @Test
    fun `given denied authorization when applying then the outcome stays action required`() = runTest {
        val providers = recordingProviders(applyOutcome = IosEnforcementOutcome.AUTHORIZATION_DENIED)
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        val report = enforcement.apply(request())

        assertEquals(EnforcementOutcome.AUTHORIZATION_REQUIRED, report.outcome)
        assertTrue(providers.calls.contains("apply"))
    }

    @Test
    fun `given an unavailable provider when applying then the outcome stays unavailable`() = runTest {
        val providers = recordingProviders(applyOutcome = IosEnforcementOutcome.UNAVAILABLE)
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        val report = enforcement.apply(request())

        assertEquals(EnforcementOutcome.UNAVAILABLE, report.outcome)
    }

    @Test
    fun `given a platform failure when applying then the outcome stays failed`() = runTest {
        val providers = recordingProviders(applyOutcome = IosEnforcementOutcome.PLATFORM_FAILURE)
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        val report = enforcement.apply(request())

        assertEquals(EnforcementOutcome.FAILED, report.outcome)
        assertFalse(report.repeatsSystemPrompt)
    }

    @Test
    fun `given a schedule failure when applying then the applied restrictions are cleared`() = runTest {
        val failed = recordingProviders(scheduleOutcome = IosSuspendedExpiryOutcome.PLATFORM_FAILURE)
        val failedEnforcement = IosSessionEnforcement(failed.enforcement, failed.expiry)

        assertEquals(EnforcementOutcome.FAILED, failedEnforcement.apply(request()).outcome)
        assertEquals(listOf("apply", "schedule", "clear"), failed.calls)

        val unauthorized = recordingProviders(scheduleOutcome = IosSuspendedExpiryOutcome.AUTHORIZATION_REQUIRED)
        val unauthorizedEnforcement = IosSessionEnforcement(unauthorized.enforcement, unauthorized.expiry)

        assertEquals(EnforcementOutcome.AUTHORIZATION_REQUIRED, unauthorizedEnforcement.apply(request()).outcome)
        assertEquals(listOf("apply", "schedule", "clear"), unauthorized.calls)
    }

    @Test
    fun `given healthy providers when clearing then the schedule is cancelled before the restrictions clear`() = runTest {
        val providers = recordingProviders()
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        val outcome = enforcement.clear()

        assertEquals(EnforcementOutcome.CLEARED, outcome)
        assertEquals(listOf("cancel", "clear"), providers.calls)
    }

    @Test
    fun `given provider states when reading status then each maps to its platform neutral outcome`() = runTest {
        val mapping = mapOf(
            IosEnforcementOutcome.APPLIED to EnforcementOutcome.APPLIED,
            IosEnforcementOutcome.CLEARED to EnforcementOutcome.CLEARED,
            IosEnforcementOutcome.AUTHORIZATION_REQUIRED to EnforcementOutcome.AUTHORIZATION_REQUIRED,
            IosEnforcementOutcome.AUTHORIZATION_DENIED to EnforcementOutcome.AUTHORIZATION_REQUIRED,
            IosEnforcementOutcome.RESTRICTED to EnforcementOutcome.AUTHORIZATION_REQUIRED,
            IosEnforcementOutcome.UNAVAILABLE to EnforcementOutcome.UNAVAILABLE,
            IosEnforcementOutcome.PLATFORM_FAILURE to EnforcementOutcome.UNKNOWN,
        )
        mapping.forEach { (providerOutcome, expected) ->
            val providers = recordingProviders(statusOutcome = providerOutcome)
            val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

            assertEquals(expected, enforcement.status(), "outcome $providerOutcome")
        }
    }

    @Test
    fun `given a cleared record when peeking then expiry reads through for the exact session`() = runTest {
        val providers = recordingProviders(reconciliation = IosExpiryReconciliation.EXPIRED)
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        assertTrue(enforcement.peekSuspendedExpiry("session"))
        assertEquals("session", providers.scheduling.seenSessionId)
    }

    @Test
    fun `given an acknowledged record when peeking then only the matching session consumes it`() = runTest {
        val providers = recordingProviders(reconciliation = IosExpiryReconciliation.EXPIRED)
        val enforcement = IosSessionEnforcement(providers.enforcement, providers.expiry)

        assertFalse(enforcement.acknowledgeSuspendedExpiry("other"))
        assertTrue(enforcement.acknowledgeSuspendedExpiry("session"))
        assertEquals(listOf("other", "session"), providers.scheduling.acknowledgedSessionIds.sorted())
    }

    private fun request(
        domains: List<String> = listOf("stable.example"),
        mappingIds: List<String> = emptyList(),
    ): EnforcementRequest {
        return EnforcementRequest(domains, mappingIds, "session", 1_700_000_000_000L, 1_700_001_800_000L)
    }

    private fun recordingProviders(
        applyOutcome: IosEnforcementOutcome = IosEnforcementOutcome.APPLIED,
        statusOutcome: IosEnforcementOutcome = IosEnforcementOutcome.CLEARED,
        scheduleOutcome: IosSuspendedExpiryOutcome = IosSuspendedExpiryOutcome.SCHEDULED,
        reconciliation: IosExpiryReconciliation = IosExpiryReconciliation.UNKNOWN,
    ): RecordingProviders {
        val calls = ArrayDeque<String>()
        val enforcement = object : IosEnforcementProvider {
            override fun apply(
                request: IosEnforcementRequest,
                completion: (IosEnforcementOutcome) -> Unit,
            ) {
                calls.addLast("apply")
                completion(applyOutcome)
            }

            override fun clear(handler: (IosEnforcementOutcome) -> Unit) {
                calls.addLast("clear")
                handler(IosEnforcementOutcome.CLEARED)
            }

            override fun status(handler: (IosEnforcementOutcome) -> Unit) {
                calls.addLast("status")
                handler(statusOutcome)
            }
        }
        val scheduling = FakeSchedulingProvider(calls, scheduleOutcome, reconciliation)
        return RecordingProviders(calls, enforcement, scheduling)
    }

    private class RecordingProviders(
        val calls: ArrayDeque<String>,
        enforcement: IosEnforcementProvider,
        val scheduling: FakeSchedulingProvider,
    ) {
        val enforcement: IosEnforcement = IosEnforcement(enforcement)
        val expiry: IosSuspendedExpiry = IosSuspendedExpiry(scheduling)
    }

    private class FakeSchedulingProvider(
        private val calls: ArrayDeque<String>,
        private val scheduleOutcome: IosSuspendedExpiryOutcome,
        private val reconciliation: IosExpiryReconciliation,
    ) : IosSuspendedExpiryProvider {
        var seenRequest: IosSuspendedExpiryRequest? = null
            private set
        var seenSessionId: String? = null
            private set
        val acknowledgedSessionIds = mutableListOf<String>()
        var reconciledSessionIds: Set<String> = setOf("session")
        var displacedSessionId: String? = null
        var scheduledSessionIds: Set<String> = emptySet()

        override fun isScheduled(
            sessionId: String,
            handler: (Boolean) -> Unit,
        ) {
            calls.addLast("isScheduled")
            handler(sessionId in scheduledSessionIds)
        }

        override fun schedule(
            request: IosSuspendedExpiryRequest,
            completion: (IosSuspendedExpiryOutcome) -> Unit,
        ) {
            calls.addLast("schedule")
            seenRequest = request
            completion(scheduleOutcome)
        }

        override fun cancel(handler: (IosSuspendedExpiryOutcome) -> Unit) {
            calls.addLast("cancel")
            handler(IosSuspendedExpiryOutcome.CANCELLED)
        }

        override fun readReconciliation(
            sessionId: String,
            handler: (IosExpiryReconciliation) -> Unit,
        ) {
            seenSessionId = sessionId
            handler(reconciliation)
        }

        override fun acknowledgeReconciliation(
            sessionId: String,
            handler: (Boolean) -> Unit,
        ) {
            calls.addLast("acknowledge")
            acknowledgedSessionIds += sessionId
            handler(sessionId in reconciledSessionIds)
        }

        override fun displacedClearedSessionId(
            currentSessionId: String,
            handler: (ExpiryDisplacement) -> Unit,
        ) {
            calls.addLast("displace")
            handler(
                ExpiryDisplacement(
                    if (displacedSessionId == null ||
                        displacedSessionId == currentSessionId
                    ) {
                        ExpiryDisplacementOutcome.ABSENT
                    } else {
                        ExpiryDisplacementOutcome.PRESENT
                    },
                    displacedSessionId,
                ),
            )
        }
    }
}
