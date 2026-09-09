package app.posato.desktop.macos

import app.posato.feature.onboarding.MacHelperReadiness
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMacHelperStateTest {
    @Test
    fun `given an unverifiable helper when enabled then the client is never touched`() = runTest {
        val commands = FakeHelperCommands({ readyResult() }, { readyResult() })
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { throw IllegalStateException("unverifiable") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { throw AssertionError("settings must stay closed") },
        )

        assertEquals(MacHelperReadiness.UNAVAILABLE, state.enable())
        assertTrue(commands.calls.isEmpty())
    }

    @Test
    fun `given a verifiable helper when enabled then enable runs before status on the io dispatcher`() = runTest {
        var dispatched = false
        val ioDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(
                context: CoroutineContext,
                block: Runnable,
            ) {
                dispatched = true
                block.run()
            }
        }
        val commands = FakeHelperCommands({ readyResult() }, { readyResult() })
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = ioDispatcher,
            openSettings = { },
        )

        assertEquals(MacHelperReadiness.READY, state.enable())
        assertEquals(listOf("enable", "status"), commands.calls.map { it.operation })
        assertTrue(dispatched)
    }

    @Test
    fun `given approval required when enabled then approval is reported without opening settings`() = runTest {
        val commands = FakeHelperCommands({ readyResult() }, { approvalRequiredResult() })
        var opened: URI? = null
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { uri -> opened = uri },
        )

        assertEquals(MacHelperReadiness.APPROVAL_REQUIRED, state.enable())
        assertEquals(null, opened)
    }

    @Test
    fun `given a failing client when enabled or rechecked then the outcome is unavailable`() = runTest {
        val commands = FakeHelperCommands(
            { throw IllegalStateException("ipc failed") },
            { throw IllegalStateException("ipc failed") },
        )
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { },
        )

        assertEquals(MacHelperReadiness.UNAVAILABLE, state.enable())
        assertEquals(MacHelperReadiness.UNAVAILABLE, state.recheck())
    }

    @Test
    fun `given approval required when rechecked then only status runs`() = runTest {
        val commands = FakeHelperCommands({ readyResult() }, { approvalRequiredResult() })
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { },
        )

        assertEquals(MacHelperReadiness.APPROVAL_REQUIRED, state.recheck())
        assertEquals(listOf("status"), commands.calls.map { it.operation })
    }

    @Test
    fun `given approval required when the settings action runs then the login items pane opens`() {
        var opened: URI? = null
        val state = DesktopMacHelperState(
            commands = FakeHelperCommands({ readyResult() }, { readyResult() }),
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { uri -> opened = uri },
        )

        state.openApprovalSettings()

        assertEquals("x-apple.systempreferences:com.apple.LoginItems-Settings.extension", opened.toString())
    }

    @Test
    fun `given settings cannot open when requested then approval remains available for manual recovery`() = runTest {
        val commands = FakeHelperCommands({ readyResult() }, { approvalRequiredResult() })
        var attempts = 0
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = {
                attempts += 1
                throw IOException("settings unavailable")
            },
        )

        state.openApprovalSettings()

        assertEquals(1, attempts)
        assertTrue(commands.calls.isEmpty())
        assertEquals(MacHelperReadiness.APPROVAL_REQUIRED, state.recheck())
    }

    @Test
    fun `given a never enabled helper when rechecked then not enabled is reported`() = runTest {
        val commands = FakeHelperCommands({ readyResult() }, { notRegisteredResult() })
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { },
        )

        assertEquals(MacHelperReadiness.NOT_ENABLED, state.recheck())
        assertEquals(listOf("status"), commands.calls.map { it.operation })
    }

    @Test
    fun `given an unverifiable helper when rechecked then the client is never touched`() = runTest {
        val commands = FakeHelperCommands({ readyResult() }, { readyResult() })
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { throw IllegalStateException("unverifiable") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { },
        )

        assertEquals(MacHelperReadiness.UNAVAILABLE, state.recheck())
        assertTrue(commands.calls.isEmpty())
    }

    @Test
    fun `given a lost helper connection when checked again then unavailable is reported both times`() = runTest {
        var statusCalls = 0
        val commands = FakeHelperCommands(
            { readyResult() },
            {
                statusCalls += 1
                if (statusCalls == 1) HelperResult.unknownOutcome() else throw IllegalStateException("pending unknown request")
            },
        )
        val state = DesktopMacHelperState(
            commands = commands,
            verifyHelper = { Path.of("/nonexistent/PosatoMacOSHelper") },
            ioDispatcher = Dispatchers.Unconfined,
            openSettings = { },
        )

        assertEquals(MacHelperReadiness.UNAVAILABLE, state.recheck())
        assertEquals(MacHelperReadiness.UNAVAILABLE, state.recheck())
        assertEquals(listOf("status", "status"), commands.calls.map { it.operation })
    }

    private fun readyResult(): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.Success,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.None,
        )
    }

    private fun approvalRequiredResult(): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.ActionRequired,
            serviceState = HelperResult.State.ApprovalRequired,
            ownershipPhase = HelperResult.Phase.Prepared,
            requiredAction = HelperResult.RequiredAction.BackgroundApproval,
            failure = HelperResult.Failure.None,
        )
    }

    private fun notRegisteredResult(): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.ActionRequired,
            serviceState = HelperResult.State.NotRegistered,
            ownershipPhase = HelperResult.Phase.RecoveryRequired,
            requiredAction = HelperResult.RequiredAction.ManualRecovery,
            failure = HelperResult.Failure.Lifecycle,
        )
    }
}

private data class HelperCall(
    val operation: String,
)

private class FakeHelperCommands(
    private val enableBehavior: () -> HelperResult,
    private val statusBehavior: () -> HelperResult,
) : MacHelperCommands {
    val calls = mutableListOf<HelperCall>()

    override fun enable(): HelperResult {
        calls.add(HelperCall("enable"))
        return enableBehavior()
    }

    override fun status(): HelperResult {
        calls.add(HelperCall("status"))
        return statusBehavior()
    }
}
