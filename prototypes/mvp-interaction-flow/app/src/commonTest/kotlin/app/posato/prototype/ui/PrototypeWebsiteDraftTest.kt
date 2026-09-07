package app.posato.prototype.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.posato.prototype.model.PrototypeWebsiteEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class PrototypeWebsiteDraftTest {
    @Test
    fun `given an acknowledged submission when an identical draft is entered later then it is retained`() {
        val browser = PrototypeItemBrowserState(PrototypeItemSection.Websites, "")
        val result = PrototypeWebsiteEntry(revision = 1, submitted = "reading.example")
        browser.websiteDraft.setTextAndPlaceCursorAtEnd(result.submitted)
        browser.acceptSubmission(result)
        assertEquals("", browser.websiteDraft.text.toString())
        browser.websiteDraft.setTextAndPlaceCursorAtEnd(result.submitted)
        browser.acceptSubmission(result)
        assertEquals(result.submitted, browser.websiteDraft.text.toString())
        browser.acceptSubmission(result.copy(revision = 2))
        assertEquals("", browser.websiteDraft.text.toString())
    }

    @Test
    fun `given edits after submitting when acknowledgment arrives then newer draft is retained`() {
        val browser = PrototypeItemBrowserState(PrototypeItemSection.Websites, "")
        browser.websiteDraft.setTextAndPlaceCursorAtEnd("newer.example")
        browser.acceptSubmission(PrototypeWebsiteEntry(revision = 1, submitted = "earlier.example"))
        assertEquals("newer.example", browser.websiteDraft.text.toString())
    }
}
