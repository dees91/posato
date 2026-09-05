package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MacOsBrowserDomainEnforcerTest {
    @Test
    fun `given empty domains when started then apply is not called`() {
        val commands = FakeBrowserDomainCommands()
        val enforcer = MacOsBrowserDomainEnforcer(commands)

        val result = enforcer.start(emptyList())

        assertIs<BrowserDomainEnforcementResult.Failed>(result)
        assertEquals(HelperResult.Failure.InvalidInput, result.result.failure)
        assertEquals(0, commands.configureCalls)
        assertEquals(0, commands.applyCalls)
    }

    @Test
    fun `given successful configure and apply when started then enforcement is active`() {
        val commands = FakeBrowserDomainCommands(configurePort = 4_443u)
        val enforcer = MacOsBrowserDomainEnforcer(commands)

        val result = enforcer.start(listOf("example.com"), 1_725_000_000_000)

        val active = assertIs<BrowserDomainEnforcementResult.Active>(result)
        assertEquals(4_443.toUShort(), active.port)
        assertEquals(1, commands.configureCalls)
        assertEquals(1, commands.applyCalls)
        assertEquals(0, commands.restoreCalls)
    }

    @Test
    fun `given configure failure when started then apply is skipped`() {
        val commands = FakeBrowserDomainCommands(
            configureResult = successHelperResult().copy(outcome = HelperResult.Outcome.Failure),
            configurePort = 0u,
        )
        val enforcer = MacOsBrowserDomainEnforcer(commands)

        val result = enforcer.start(listOf("example.com"))

        assertIs<BrowserDomainEnforcementResult.Failed>(result)
        assertEquals(1, commands.configureCalls)
        assertEquals(0, commands.applyCalls)
    }

    @Test
    fun `given unknown apply when started then reconcile runs and restore follows failure`() {
        val commands = FakeBrowserDomainCommands(
            configurePort = 4_443u,
            applyResults = mutableListOf(HelperResult.unknownOutcome()),
            reconcileResults = mutableListOf(
                successHelperResult().copy(outcome = HelperResult.Outcome.Failure),
            ),
        )
        val enforcer = MacOsBrowserDomainEnforcer(commands)

        val result = enforcer.start(listOf("example.com"))

        assertIs<BrowserDomainEnforcementResult.Failed>(result)
        assertEquals(1, commands.applyCalls)
        assertEquals(1, commands.reconcileCalls)
        assertEquals(1, commands.restoreCalls)
    }

    @Test
    fun `given unknown restore after failed apply when started then reconcile clears the unknown`() {
        val commands = FakeBrowserDomainCommands(
            configurePort = 4_443u,
            applyResults = mutableListOf(successHelperResult().copy(outcome = HelperResult.Outcome.Failure)),
            restoreResults = mutableListOf(
                HelperResult.unknownOutcome(),
                successHelperResult().copy(ownershipPhase = HelperResult.Phase.Idle),
            ),
            reconcileResults = mutableListOf(
                successHelperResult().copy(ownershipPhase = HelperResult.Phase.Idle),
            ),
        )
        val enforcer = MacOsBrowserDomainEnforcer(commands)

        val result = enforcer.start(listOf("example.com"))

        assertIs<BrowserDomainEnforcementResult.Failed>(result)
        assertEquals(1, commands.restoreCalls)
        assertEquals(1, commands.reconcileCalls)
        assertEquals(HelperResult.Outcome.Success, enforcer.clear().outcome)
    }

    @Test
    fun `given successful apply without the applied phase when started then restore follows and the result is failed`() {
        val commands = FakeBrowserDomainCommands(
            configurePort = 4_443u,
            applyResults = mutableListOf(successHelperResult().copy(ownershipPhase = HelperResult.Phase.Idle)),
        )
        val enforcer = MacOsBrowserDomainEnforcer(commands)

        val result = enforcer.start(listOf("example.com"))

        assertIs<BrowserDomainEnforcementResult.Failed>(result)
        assertEquals(HelperResult.Phase.Idle, result.result.ownershipPhase)
        assertEquals(1, commands.restoreCalls)
    }

    @Test
    fun `given unknown apply reconciled to idle when started then restore follows and the result is failed`() {
        val commands = FakeBrowserDomainCommands(
            configurePort = 4_443u,
            applyResults = mutableListOf(HelperResult.unknownOutcome()),
            reconcileResults = mutableListOf(successHelperResult().copy(ownershipPhase = HelperResult.Phase.Idle)),
        )
        val enforcer = MacOsBrowserDomainEnforcer(commands)

        val result = enforcer.start(listOf("example.com"))

        assertIs<BrowserDomainEnforcementResult.Failed>(result)
        assertEquals(1, commands.reconcileCalls)
        assertEquals(1, commands.restoreCalls)
    }

    @Test
    fun `given sensitive values when rendered then they remain redacted`() {
        val active = BrowserDomainEnforcementResult.Active(4_443u)
        val failed = BrowserDomainEnforcementResult.Failed(successHelperResult())

        assertTrue(active.toString().contains("redacted"))
        assertTrue(failed.toString().contains("redacted"))
        assertFalse(active.toString().contains("4443"))
    }

    private fun successHelperResult(): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.Success,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Applied,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.None,
        )
    }

    private class FakeBrowserDomainCommands(
        private val configureResult: HelperResult = HelperResult(
            outcome = HelperResult.Outcome.Success,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.None,
        ),
        private val configurePort: UShort = 0u,
        private val applyResults: MutableList<HelperResult> = mutableListOf(
            HelperResult(
                outcome = HelperResult.Outcome.Success,
                serviceState = HelperResult.State.Ready,
                ownershipPhase = HelperResult.Phase.Applied,
                requiredAction = HelperResult.RequiredAction.None,
                failure = HelperResult.Failure.None,
            ),
        ),
        private val restoreResults: MutableList<HelperResult> = mutableListOf(),
        private val reconcileResults: MutableList<HelperResult> = mutableListOf(),
    ) : MacOsBrowserDomainCommands {
        var configureCalls = 0
        var applyCalls = 0
        var restoreCalls = 0
        var reconcileCalls = 0

        override fun configureBrowserDomains(
            domains: List<String>,
            sessionEndEpochMilliseconds: Long?,
        ): BrowserDomainConfigureResponse {
            configureCalls += 1
            return BrowserDomainConfigureResponse(configureResult, configurePort)
        }

        override fun apply(port: UShort): HelperResult {
            applyCalls += 1
            return applyResults.removeFirst()
        }

        override fun restore(): HelperResult {
            restoreCalls += 1
            if (restoreResults.isNotEmpty()) {
                return restoreResults.removeFirst()
            }
            return successRestore()
        }

        override fun reconcileUnknown(): HelperResult {
            reconcileCalls += 1
            return reconcileResults.removeFirst()
        }

        private fun successRestore(): HelperResult {
            return HelperResult(
                outcome = HelperResult.Outcome.Success,
                serviceState = HelperResult.State.Ready,
                ownershipPhase = HelperResult.Phase.Idle,
                requiredAction = HelperResult.RequiredAction.None,
                failure = HelperResult.Failure.None,
            )
        }
    }
}
