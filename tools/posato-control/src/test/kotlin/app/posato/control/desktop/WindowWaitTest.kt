package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WindowWaitTest {
    private var now = 0L
    private val slept = mutableListOf<Long>()

    private fun await(
        state: WindowState = WindowState.PRESENT,
        probe: WindowProbe
    ) = WindowWait.await(
        timeoutSeconds = 1.0,
        describe = "PosatoMacOSHelper",
        state = state,
        clock = { now },
        sleep = { millis ->
            slept.add(millis)
            now += millis
        },
        probe = probe,
    )

    @Test
    fun `a window that is already there returns without waiting`() {
        await { true }
        assertTrue(slept.isEmpty())
    }

    @Test
    fun `a process that appears during the wait is accepted`() {
        var polls = 0
        await {
            polls++
            if (polls < 3) throw ControlException(ErrorCode.PROCESS_NOT_ALLOWED, "not started yet")
            true
        }
        assertEquals(3, polls)
    }

    @Test
    fun `a selector that never resolves ends as its own refusal, not as a timeout`() {
        val failure = assertFailsWith<ControlException> {
            await { throw ControlException(ErrorCode.PROCESS_NOT_ALLOWED, "No process inside the staged Posato.app runs 'Safari'.") }
        }
        assertEquals(ErrorCode.PROCESS_NOT_ALLOWED, failure.code)
        assertEquals(3, failure.code.exitCode)
        assertTrue(failure.message.orEmpty().contains("Safari"), failure.message)
    }

    @Test
    fun `no tracked application is reported at once instead of burning the timeout`() {
        val failure = assertFailsWith<ControlException> {
            await { throw ControlException(ErrorCode.APP_NOT_RUNNING, "No tracked desktop process is running.") }
        }
        assertEquals(ErrorCode.APP_NOT_RUNNING, failure.code)
        assertEquals(3, failure.code.exitCode)
        assertTrue(slept.isEmpty(), "the refusal must not wait")
    }

    @Test
    fun `a window that never appears is a timeout`() {
        val failure = assertFailsWith<ControlException> { await { false } }
        assertEquals(ErrorCode.WAIT_TIMEOUT, failure.code)
        assertTrue(failure.message.orEmpty().contains("PosatoMacOSHelper"), failure.message)
    }

    @Test
    fun `a window that is already gone satisfies the absent wait`() {
        await(WindowState.ABSENT) { false }
        assertTrue(slept.isEmpty())
    }

    @Test
    fun `a process that exits satisfies the absent wait, because its window cannot outlive it`() {
        var polls = 0
        await(WindowState.ABSENT) {
            polls++
            if (polls < 2) true else throw ControlException(ErrorCode.PROCESS_NOT_ALLOWED, "no longer running")
        }
        assertEquals(2, polls)
    }

    @Test
    fun `a window that never closes is a timeout naming the close`() {
        val failure = assertFailsWith<ControlException> { await(WindowState.ABSENT) { true } }
        assertEquals(ErrorCode.WAIT_TIMEOUT, failure.code)
        assertTrue(failure.message.orEmpty().contains("close"), failure.message)
    }

    @Test
    fun `a refusal that clears before the deadline is not reported after a later timeout`() {
        var polls = 0
        val failure = assertFailsWith<ControlException> {
            await {
                polls++
                if (polls == 1) throw ControlException(ErrorCode.PROCESS_NOT_ALLOWED, "not started yet")
                false
            }
        }
        assertEquals(ErrorCode.WAIT_TIMEOUT, failure.code)
    }
}
