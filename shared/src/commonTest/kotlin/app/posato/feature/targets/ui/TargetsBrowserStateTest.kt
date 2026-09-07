package app.posato.feature.targets.ui

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TargetsBrowserStateTest {
    @Test
    fun `given acknowledged input when a new draft is typed then old feedback is hidden`() {
        val browser = TargetsBrowserState()
        browser.websiteDraft.edit { append("first.example") }
        browser.submit { _, _ -> }
        browser.accept(WebsiteBatchReceipt(1, saved = true, addedCount = 1))

        browser.websiteDraft.edit { append("next.example") }

        assertNull(browser.lastReceipt)
    }

    @Test
    fun `given an editing session when revisited then its unsaved draft survives`() {
        val browser = TargetsBrowserState()
        browser.editorDraft(1, "first.example").edit { replace(0, length, "changed.example") }

        assertEquals("changed.example", browser.editorDraft(1, "first.example").text.toString())
        assertEquals("second.example", browser.editorDraft(2, "second.example").text.toString())
    }

    @Test
    fun `given a mixed batch when acknowledged then only rejected entries remain`() {
        val browser = TargetsBrowserState()
        browser.websiteDraft.edit { append("first.example, invalid\nsecond.example") }
        browser.submit { _, _ -> }

        browser.accept(WebsiteBatchReceipt(1, saved = true, addedCount = 2, rejectedIndices = persistentListOf(1)))

        assertEquals("invalid", browser.websiteDraft.text.toString())
        assertEquals(2, browser.lastReceipt?.addedCount)
    }

    @Test
    fun `given newer text when an earlier save completes then the new draft is untouched`() {
        val browser = TargetsBrowserState()
        browser.websiteDraft.edit { append("first.example") }
        browser.submit { _, _ -> }
        browser.websiteDraft.edit { replace(0, length, "next.example") }

        browser.accept(WebsiteBatchReceipt(1, saved = true, addedCount = 1))

        assertEquals("next.example", browser.websiteDraft.text.toString())
        assertNull(browser.lastReceipt)
        var nextId: Long? = null
        browser.submit { _, id -> nextId = id }
        assertEquals(2L, nextId)
    }

    @Test
    fun `given a failed save when acknowledged then retry keeps the whole draft`() {
        val browser = TargetsBrowserState()
        val input = "https://first.example/path, second.example"
        browser.websiteDraft.edit { append(input) }
        browser.submit { _, _ -> }

        browser.accept(WebsiteBatchReceipt(1, saved = false))

        assertEquals(input, browser.websiteDraft.text.toString())
        var retriedInput: String? = null
        browser.submit { value, _ -> retriedInput = value }
        assertEquals(input, retriedInput)
    }

    @Test
    fun `given a pending submission when submitted again then its acknowledgement is not displaced`() {
        val browser = TargetsBrowserState()
        browser.websiteDraft.edit { append("first.example") }
        var calls = 0
        browser.submit { _, _ -> calls++ }
        browser.submit { _, _ -> calls++ }

        browser.accept(WebsiteBatchReceipt(1, saved = true, addedCount = 1))

        assertEquals(1, calls)
        assertEquals("", browser.websiteDraft.text.toString())
    }

    @Test
    fun `given another submission receipt when acknowledged then the pending draft remains`() {
        val browser = TargetsBrowserState()
        browser.websiteDraft.edit { append("first.example") }
        browser.submit { _, _ -> }

        browser.accept(WebsiteBatchReceipt(0, saved = true, addedCount = 1))

        assertEquals("first.example", browser.websiteDraft.text.toString())
        assertNull(browser.lastReceipt)
        browser.accept(WebsiteBatchReceipt(1, saved = true, addedCount = 1))
        assertEquals("", browser.websiteDraft.text.toString())
    }
}
