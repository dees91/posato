package app.posato.desktop.macos

import app.posato.feature.targets.data.LocalApplicationMappingId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MacOsApplicationEnforcerTest {
    @Test
    fun `given mapped ids when started then resolution precedes configure and verified active follows`() {
        val first = mappingId("aa")
        val second = mappingId("bb")
        val blobs = listOf(byteArrayOf(1), byteArrayOf(2))
        val calls = ArrayDeque<String>()
        val commands = recordingCommands(calls, ApplicationEnforcementResponse(success(), blobs.size))
        val enforcer = MacOsApplicationEnforcer(
            commands = commands,
            requirements = { ids ->
                calls.addLast("resolve")
                assertEquals(listOf(first, second), ids)
                blobs
            },
        )

        val started = assertIs<ApplicationEnforcementResult.Active>(runBlocking { enforcer.start(listOf(first, second)) })

        assertEquals(blobs.size, started.acceptedCount)
        assertEquals(listOf("resolve", "configure"), calls.toList())
    }

    @Test
    fun `given empty ids when started then no request leaves the client`() {
        val calls = ArrayDeque<String>()
        val enforcer = MacOsApplicationEnforcer(
            commands = recordingCommands(calls, ApplicationEnforcementResponse(success(), 0)),
            requirements = { error("must not resolve") },
        )

        val started = runBlocking { enforcer.start(emptyList()) }

        val failed = assertIs<ApplicationEnforcementResult.Failed>(started)
        assertEquals(HelperResult.Failure.InvalidInput, failed.result.failure)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `given unknown ids when started then they are reported as invalid input`() {
        val enforcer = MacOsApplicationEnforcer(
            commands = recordingCommands(ArrayDeque(), ApplicationEnforcementResponse(success(), 0)),
            requirements = { throw IllegalArgumentException("Unknown application mapping") },
        )

        val started = runBlocking { enforcer.start(listOf(mappingId("cc"))) }

        val failed = assertIs<ApplicationEnforcementResult.Failed>(started)
        assertEquals(HelperResult.Failure.InvalidInput, failed.result.failure)
    }

    @Test
    fun `given storage failure when started then it is reported without configuring`() {
        val calls = ArrayDeque<String>()
        val enforcer = MacOsApplicationEnforcer(
            commands = recordingCommands(calls, ApplicationEnforcementResponse(success(), 1)),
            requirements = { throw IllegalStateException("database is unavailable") },
        )

        val started = runBlocking { enforcer.start(listOf(mappingId("dd"))) }

        val failed = assertIs<ApplicationEnforcementResult.Failed>(started)
        assertEquals(HelperResult.Failure.Storage, failed.result.failure)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `given accepted count mismatch when started then it fails without claiming active`() {
        val enforcer = MacOsApplicationEnforcer(
            commands = recordingCommands(
                ArrayDeque(),
                ApplicationEnforcementResponse(success(), 0),
            ),
            requirements = { listOf(byteArrayOf(3)) },
        )

        val started = runBlocking { enforcer.start(listOf(mappingId("ee"))) }

        assertIs<ApplicationEnforcementResult.Failed>(started)
    }

    @Test
    fun `given unknown configure outcome when started then it fails because observation is gone`() {
        val enforcer = MacOsApplicationEnforcer(
            commands = recordingCommands(
                ArrayDeque(),
                ApplicationEnforcementResponse(HelperResult.unknownOutcome(), 0),
            ),
            requirements = { listOf(byteArrayOf(4)) },
        )

        val started = runBlocking { enforcer.start(listOf(mappingId("ff"))) }

        val failed = assertIs<ApplicationEnforcementResult.Failed>(started)
        assertEquals(HelperResult.Outcome.UnknownOutcome, failed.result.outcome)
    }

    @Test
    fun `given active session when cleared then an empty configure stops observation`() {
        val seen = ArrayDeque<List<ByteArray>>()
        val cleared = HelperResult(
            outcome = HelperResult.Outcome.Success,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.None,
        )
        val commands = MacOsApplicationCommands { requirements, end ->
            seen.addLast(requirements)
            assertEquals(null, end)
            ApplicationEnforcementResponse(cleared, 0)
        }
        val enforcer = MacOsApplicationEnforcer(commands = commands, requirements = { emptyList() })

        val result = runBlocking { enforcer.clear() }

        assertEquals(cleared, result)
        assertEquals(1, seen.size)
        assertTrue(seen.single().isEmpty())
    }

    private fun mappingId(seed: String): LocalApplicationMappingId {
        return LocalApplicationMappingId.restore(seed.repeat(32)) ?: error("invalid mapping id")
    }

    private fun success(): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.Success,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.None,
        )
    }

    private fun recordingCommands(
        calls: ArrayDeque<String>,
        response: ApplicationEnforcementResponse,
    ): MacOsApplicationCommands {
        return MacOsApplicationCommands { requirements, _ ->
            calls.addLast("configure")
            response
        }
    }
}
