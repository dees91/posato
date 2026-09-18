package app.posato.feature.targets.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebsiteBatchSubmissionTest {
    @Test
    fun `given domains and URLs when submitted then only canonical exact hosts survive`() {
        val result = ready("EXAMPLE.COM, https://www.example.com/private?q=secret#fragment\nbücher.example")

        assertEquals(listOf("example.com", "xn--bcher-kva.example"), result.canonicalDomains)
        assertEquals(2, result.addedCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(emptyList(), result.rejectedIndices)
        assertEquals("WebsiteBatchSubmission.Ready(redacted)", result.toString())
    }

    @Test
    fun `given duplicate and invalid entries when submitted then rejected positions remain identifiable`() {
        val result = ready("example.com, EXAMPLE.COM, invalid, https://other.example/path", listOf("example.com"))

        assertEquals(listOf("example.com", "other.example"), result.canonicalDomains)
        assertEquals(1, result.addedCount)
        assertEquals(2, result.duplicateCount)
        assertEquals(listOf(2), result.rejectedIndices)
    }

    @Test
    fun `given credentials unsupported schemes and IPs when submitted then no host is accepted`() {
        val input = listOf(
            "https://user:password@example.com/path",
            "https://@example.com",
            "ftp://example.com",
            "https://127.0.0.1",
            "https://[::1]",
            "*.example.com",
            "https:///example.com",
        ).joinToString(",")

        val result = ready(input)

        assertEquals(emptyList(), result.canonicalDomains)
        assertEquals((0..6).toList(), result.rejectedIndices)
    }

    @Test
    fun `given a nearly full policy when submitted then capacity applies only to new unique domains`() {
        val existing = (1..1023).map { "site$it.example" }
        val result = ready("site1.example, new.example, overflow.example, new.example", existing)

        assertEquals(1024, result.canonicalDomains.size)
        assertEquals(1, result.addedCount)
        assertEquals(2, result.duplicateCount)
        assertEquals(listOf(2), result.rejectedIndices)
    }

    @Test
    fun `given oversized input when submitted then the whole batch is rejected before parsing`() {
        assertIs<WebsiteBatchSubmission.TooLong>(createWebsiteBatchSubmission("a".repeat(65_537), emptyList()))
    }

    @Test
    fun `given empty separators and oversized entries when submitted then valid entries still survive`() {
        val result = ready(",\r\n example.com,,${"a".repeat(1025)}\n")

        assertEquals(listOf("example.com"), result.canonicalDomains)
        assertEquals(listOf(1), result.rejectedIndices)
    }

    @Test
    fun `given a www counterpart of a listed host when submitted then it counts as a duplicate`() {
        val result = ready("www.example.com", listOf("example.com"))

        assertEquals(listOf("example.com"), result.canonicalDomains)
        assertEquals(0, result.addedCount)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun `given both hosts in one batch when submitted then only the first is stored`() {
        val result = ready("example.com, www.example.com")

        assertEquals(listOf("example.com"), result.canonicalDomains)
        assertEquals(1, result.addedCount)
        assertEquals(1, result.duplicateCount)
    }
}

private fun ready(
    input: String,
    existing: List<String> = emptyList()
): WebsiteBatchSubmission.Ready {
    return assertIs<WebsiteBatchSubmission.Ready>(createWebsiteBatchSubmission(input, existing))
}
