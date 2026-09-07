package app.posato.prototype.ui

import app.posato.prototype.model.PrototypeFixtures
import app.posato.prototype.model.PrototypePlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrototypeItemSearchTest {
    @Test
    fun `given a long selection when searching then matches are case insensitive and whitespace tolerant`() {
        val websites = PrototypeFixtures.longList(PrototypePlatform.Mac).policy.domains
        assertEquals(listOf("reading-50.example"), filterPrototypeWebsites(websites, "  READING-50  "))
        assertEquals(websites.take(9), filterPrototypeWebsites(websites, "reading-0"))
        assertEquals(50, websites.size)
    }

    @Test
    fun `given a search when cleared then the complete original order returns`() {
        val websites = persistentListOf("zebra.example", "reading.example", "alpha.example")
        assertEquals(websites, filterPrototypeWebsites(websites, ""))
        assertEquals(websites, filterPrototypeWebsites(websites, " \t "))
        assertEquals(websites, filterPrototypeWebsites(websites, ".example"))
    }

    @Test
    fun `given no matching website or an empty selection when searching then no result is returned`() {
        assertTrue(filterPrototypeWebsites(persistentListOf("reading.example"), "social").isEmpty())
        assertTrue(filterPrototypeWebsites(persistentListOf(), "reading").isEmpty())
    }
}
