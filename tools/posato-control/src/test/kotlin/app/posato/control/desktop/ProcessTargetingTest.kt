package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val BUNDLE = "/staged/Posato.app"
private const val APPLICATION = "$BUNDLE/Contents/MacOS/Posato"
private const val HELPER = "$BUNDLE/Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper"
private const val COMPANION = "$BUNDLE/Contents/Helpers/PosatoMacOSSync.app/Contents/MacOS/PosatoMacOSSync"

class ProcessTargetingTest {
    private val contained = listOf(
        ProcessEntry(100, APPLICATION),
        ProcessEntry(200, HELPER),
        ProcessEntry(300, COMPANION),
    )

    private fun resolve(
        selector: String?,
        trackedPid: Long? = 100,
        candidates: List<ProcessEntry> = contained
    ): Long = ProcessTargeting.resolve(selector, trackedPid) { candidates }

    @Test
    fun `no selector keeps the tracked application`() {
        assertEquals(100, resolve(null))
    }

    @Test
    fun `no selector never enumerates processes`() {
        assertEquals(100, ProcessTargeting.resolve(null, 100) { error("the process list must not be read") })
    }

    @Test
    fun `a contained executable name resolves to its pid`() {
        assertEquals(200, resolve("PosatoMacOSHelper"))
        assertEquals(300, resolve("PosatoMacOSSync"))
    }

    @Test
    fun `a contained pid resolves to itself`() {
        assertEquals(200, resolve("200"))
    }

    @Test
    fun `a pid outside the staged bundle is refused as a precondition`() {
        val failure = assertFailsWith<ControlException> { resolve("4711") }
        assertEquals(ErrorCode.PROCESS_NOT_ALLOWED, failure.code)
        assertEquals(3, failure.code.exitCode)
        assertTrue(failure.message.orEmpty().contains("4711"), failure.message)
        assertTrue(failure.hint.orEmpty().contains("PosatoMacOSHelper"), failure.hint)
    }

    @Test
    fun `an unknown name is refused and names the addressable processes`() {
        val failure = assertFailsWith<ControlException> { resolve("Safari") }
        assertEquals(ErrorCode.PROCESS_NOT_ALLOWED, failure.code)
        assertTrue(failure.hint.orEmpty().contains("PosatoMacOSSync"), failure.hint)
    }

    @Test
    fun `an ambiguous name is refused rather than guessed`() {
        val duplicates = contained + ProcessEntry(201, HELPER)
        val failure = assertFailsWith<ControlException> { resolve("PosatoMacOSHelper", candidates = duplicates) }
        assertEquals(ErrorCode.PROCESS_NOT_ALLOWED, failure.code)
        assertTrue(failure.message.orEmpty().contains("200"), failure.message)
        assertTrue(failure.message.orEmpty().contains("201"), failure.message)
    }

    @Test
    fun `a valid selector is still refused while no tracked application runs`() {
        val failure = assertFailsWith<ControlException> { resolve("PosatoMacOSHelper", trackedPid = null) }
        assertEquals(ErrorCode.APP_NOT_RUNNING, failure.code)
        assertEquals(3, failure.code.exitCode)
    }

    @Test
    fun `no selector is refused while no tracked application runs`() {
        val failure = assertFailsWith<ControlException> { resolve(null, trackedPid = null) }
        assertEquals(ErrorCode.APP_NOT_RUNNING, failure.code)
    }

    @Test
    fun `a refusal with nothing addressable still explains the rule`() {
        val failure = assertFailsWith<ControlException> { resolve("PosatoMacOSHelper", candidates = emptyList()) }
        assertTrue(failure.hint.orEmpty().contains("inside the staged Posato.app"), failure.hint)
    }
}
