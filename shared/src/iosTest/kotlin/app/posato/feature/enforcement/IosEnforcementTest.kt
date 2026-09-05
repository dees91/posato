package app.posato.feature.enforcement

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosEnforcementTest {
    @Test
    fun `given empty domains and mapping ids when applied then nothing is enforced without touching the provider`() = runTest {
        val provider = FakeIosEnforcementProvider(applyOutcome = IosEnforcementOutcome.APPLIED)

        assertEquals(
            IosEnforcementOutcome.NOTHING_TO_ENFORCE,
            IosEnforcement(provider).apply(IosEnforcementRequest(emptyList(), emptyList())),
        )
        assertFalse(provider.applyCalled)
    }

    @Test
    fun `given content when applied then every provider outcome is preserved and the request is forwarded`() = runTest {
        IosEnforcementOutcome.entries.forEach { outcome ->
            val provider = FakeIosEnforcementProvider(applyOutcome = outcome)
            val request = IosEnforcementRequest(listOf("example.com"), listOf(identifier()))

            assertEquals(outcome, IosEnforcement(provider).apply(request), "outcome $outcome")
            assertEquals(request, provider.seenRequest, "outcome $outcome")
        }
    }

    @Test
    fun `given a clear when completed then the provider outcome is preserved`() = runTest {
        val outcomes = listOf(
            IosEnforcementOutcome.CLEARED,
            IosEnforcementOutcome.PLATFORM_FAILURE,
            IosEnforcementOutcome.UNAVAILABLE,
        )

        outcomes.forEach { outcome ->
            val provider = FakeIosEnforcementProvider(clearOutcome = outcome)

            assertEquals(outcome, IosEnforcement(provider).clear(), "outcome $outcome")
            assertTrue(provider.clearCalled, "outcome $outcome")
        }
    }

    @Test
    fun `given the new enforcement carrier when described then values stay redacted`() {
        assertEquals(
            "IosEnforcementRequest(redacted)",
            IosEnforcementRequest(listOf("example.com"), listOf(identifier())).toString(),
        )
    }

    private fun identifier(character: Char = '0'): String {
        return character.toString().repeat(64)
    }
}

private class FakeIosEnforcementProvider(
    private val applyOutcome: IosEnforcementOutcome = IosEnforcementOutcome.APPLIED,
    private val clearOutcome: IosEnforcementOutcome = IosEnforcementOutcome.CLEARED,
) : IosEnforcementProvider {
    var applyCalled = false
        private set
    var clearCalled = false
        private set
    var seenRequest: IosEnforcementRequest? = null
        private set

    override fun apply(
        request: IosEnforcementRequest,
        completion: (IosEnforcementOutcome) -> Unit,
    ) {
        applyCalled = true
        seenRequest = request
        completion(applyOutcome)
    }

    override fun clear(handler: (IosEnforcementOutcome) -> Unit) {
        clearCalled = true
        handler(clearOutcome)
    }
}
