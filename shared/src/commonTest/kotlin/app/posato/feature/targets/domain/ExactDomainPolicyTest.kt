package app.posato.feature.targets.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExactDomainPolicyTest {
    @Test
    fun `given ascii input when parsed then whitespace case and one terminal dot are canonicalized`() {
        val domain = assertSuccess(ExactDomain.parse("  EXAMPLE.COM.  "))

        assertEquals("example.com", domain.canonicalValue)
    }

    @Test
    fun `given unicode input when parsed then a deterministic A-label is returned`() {
        val input = "b" + Char(0x00FC) + "cher.example"

        val domain = assertSuccess(ExactDomain.parse(input))

        assertEquals("xn--bcher-kva.example", domain.canonicalValue)
    }

    @Test
    fun `given oversized collapsing unicode input when parsed then it fails before canonicalization`() {
        val ignoredCharacter = Char(0x00AD)
        val input = ignoredCharacter.toString().repeat(ExactDomainPolicyLimits.MAX_RAW_INPUT_LENGTH) + "a.example"

        val failure = assertFailure(ExactDomain.parse(input))

        assertEquals(ExactDomainInputFailure.TOO_LONG, failure.reason)
    }

    @Test
    fun `given malformed or out of scope input when parsed then it fails`() {
        val invalidInputs = listOf(
            "",
            "example",
            "https://example.com",
            "127.0.0.1",
            "example.com..",
            "ab--cd.example",
            "xn--0.example",
            "-start.example",
            "end-.example",
            "a".repeat(64) + ".example",
        )

        invalidInputs.forEach { input ->
            assertIs<ExactDomainInputResult.Failure>(ExactDomain.parse(input))
        }
    }

    @Test
    fun `given WHATWG number-ending hosts when parsed or restored then they fail`() {
        val invalidInputs = listOf(
            "0x7f.0x0.0x0.0x1",
            "127.0.0x0.1",
            "example.123",
            "example.0xdead",
            "example.0x",
        )

        invalidInputs.forEach { input ->
            assertIs<ExactDomainInputResult.Failure>(ExactDomain.parse(input))
            assertNull(ExactDomain.restore(input))
        }
    }

    @Test
    fun `given a hexadecimal label before the final label when parsed then it remains a domain`() {
        val domain = assertSuccess(ExactDomain.parse("0x7f.example"))

        assertEquals("0x7f.example", domain.canonicalValue)
    }

    @Test
    fun `given canonical A-label storage values when restored then only strict round trips are accepted`() {
        assertEquals(
            "xn--bcher-kva.example",
            ExactDomain.restore("xn--bcher-kva.example")?.canonicalValue,
        )
        assertNull(ExactDomain.restore("xn--pokxncvks.example"))
        assertNull(ExactDomain.restore("xn--a.example"))
    }

    @Test
    fun `given a domain when rendered as text then its value remains redacted`() {
        val domain = assertSuccess(ExactDomain.parse("private.example"))

        assertEquals("ExactDomain(redacted)", domain.toString())
    }

    @Test
    fun `given a bare host when a counterpart is derived then the www host is returned`() {
        val domain = assertSuccess(ExactDomain.parse("example.com"))

        assertEquals("www.example.com", domain.wwwCounterpart()?.canonicalValue)
    }

    @Test
    fun `given a www host when a counterpart is derived then the bare host is returned`() {
        val domain = assertSuccess(ExactDomain.parse("www.example.com"))

        assertEquals("example.com", domain.wwwCounterpart()?.canonicalValue)
    }

    @Test
    fun `given a multi-label www host when a counterpart is derived then one www label is stripped`() {
        val domain = assertSuccess(ExactDomain.parse("www.news.example.com"))

        assertEquals("news.example.com", domain.wwwCounterpart()?.canonicalValue)
    }

    @Test
    fun `given an IDN host when a counterpart is derived then the A-label www host is returned`() {
        val input = "b" + Char(0x00FC) + "cher.example"
        val domain = assertSuccess(ExactDomain.parse(input))

        assertEquals("www.xn--bcher-kva.example", domain.wwwCounterpart()?.canonicalValue)
    }

    @Test
    fun `given a www plus public suffix when a counterpart is derived then the single-label remainder is rejected`() {
        val domain = assertSuccess(ExactDomain.parse("www.com"))

        assertNull(domain.wwwCounterpart())
    }

    @Test
    fun `given a doubled www host when a counterpart is derived then one www label is stripped`() {
        val domain = assertSuccess(ExactDomain.parse("www.www.example.com"))

        assertEquals("www.example.com", domain.wwwCounterpart()?.canonicalValue)
    }
}

private fun assertSuccess(result: ExactDomainInputResult): ExactDomain {
    return assertIs<ExactDomainInputResult.Success>(result).domain
}

private fun assertFailure(result: ExactDomainInputResult): ExactDomainInputResult.Failure {
    return assertIs<ExactDomainInputResult.Failure>(result)
}
