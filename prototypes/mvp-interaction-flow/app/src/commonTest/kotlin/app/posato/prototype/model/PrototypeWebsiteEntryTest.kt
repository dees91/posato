package app.posato.prototype.model

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrototypeWebsiteEntryTest {
    private fun items(): PrototypeState {
        return reducePrototype(PrototypeFixtures.ready(PrototypePlatform.Mac), ItemAction.OpenItems)
    }

    @Test
    fun `given pasted websites when added then exact hosts are unique and ordered`() {
        val before = items()
        val input = "https://READING.example/article?q=private\nnews.example, reading.example, video.example"
        val after = reducePrototype(before, ItemAction.AddDomains(input))
        assertEquals(persistentListOf("news.example", "reading.example", "video.example"), after.policy.domains)
        assertEquals(PrototypeSurface.Items, after.surface)
        assertEquals("", after.websiteEntry.remainder)
        assertEquals(2, after.websiteEntry.addedCount)
        assertEquals(2, after.websiteEntry.duplicateCount)
        assertTrue(after.sync.pendingWork)
    }

    @Test
    fun `given mixed batch when submitted then invalid entries remain and valid entries are saved`() {
        val after = reducePrototype(items(), ItemAction.AddDomains("reading.example\nlocalhost\nftp://video.example\nvideo.example"))
        assertEquals(persistentListOf("news.example", "reading.example", "video.example"), after.policy.domains)
        assertEquals("localhost\nftp://video.example", after.websiteEntry.remainder)
        assertEquals(2, after.websiteEntry.invalidCount)
        assertTrue(after.websiteEntry.message.contains("2 added"))
        val fixed = reducePrototype(after, ItemAction.AddDomains("fixed.example\nhttps://video.example"))
        assertEquals(1, fixed.websiteEntry.addedCount)
        assertEquals(1, fixed.websiteEntry.duplicateCount)
        assertEquals("", fixed.websiteEntry.remainder)
        assertEquals(after.websiteEntry.revision + 1, fixed.websiteEntry.revision)
    }

    @Test
    fun `given duplicate or empty batch when submitted then no pending work is invented`() {
        val before = items().copy(sync = PrototypeSync())
        for (input in listOf("news.example", "  , \n ", "localhost")) {
            val after = reducePrototype(before, ItemAction.AddDomains(input))
            assertEquals(before.policy, after.policy)
            assertFalse(after.sync.pendingWork)
            assertTrue(after.websiteEntry.message.isNotBlank())
        }
    }

    @Test
    fun `given fifteen websites when submitted twice then all are added once`() {
        val input = (1..15).joinToString("\n") { "site$it.example" }
        val added = reducePrototype(items(), ItemAction.AddDomains(input))
        val repeated = reducePrototype(added, ItemAction.AddDomains(input))
        assertEquals(16, repeated.policy.domains.size)
        assertEquals(15, repeated.websiteEntry.duplicateCount)
        assertEquals(added.policy, repeated.policy)
    }

    @Test
    fun `given a non-management surface when adding websites then mutation is blocked`() {
        for (before in listOf(PrototypeFixtures.ready(PrototypePlatform.Mac), PrototypeFixtures.active(PrototypePlatform.Mac))) {
            val after = reducePrototype(before, ItemAction.AddDomains("reading.example"))
            assertEquals(before.policy, after.policy)
            assertEquals(before.websiteEntry, after.websiteEntry)
            assertEquals(OutcomeTone.Blocked, after.outcome.tone)
        }
    }
}
