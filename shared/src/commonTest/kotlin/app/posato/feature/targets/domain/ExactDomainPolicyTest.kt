package app.posato.feature.targets.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `given the shared www vector when a counterpart is derived then Kotlin matches the table`() {
        WWW_COUNTERPART_VECTOR.forEach { (host, counterpart) ->
            assertEquals(counterpart, assertSuccess(ExactDomain.parse(host)).wwwCounterpart()?.canonicalValue, host)
        }
        WWW_WITHOUT_COUNTERPART.forEach { host ->
            assertNull(assertSuccess(ExactDomain.parse(host)).wwwCounterpart(), host)
        }
    }

    @Test
    fun `given a listed host when covering is checked then the counterpart is covered and doubled www is one step`() {
        val apex = assertSuccess(ExactDomain.parse("example.com"))
        val www = assertSuccess(ExactDomain.parse("www.example.com"))
        val doubled = assertSuccess(ExactDomain.parse("www.www.example.com"))

        assertTrue(apex.covers(www))
        assertTrue(www.covers(apex))
        assertTrue(doubled.covers(www))
        assertFalse(doubled.covers(apex))
        assertFalse(apex.covers(doubled))
    }
}

internal val WWW_COUNTERPART_VECTOR: List<Pair<String, String>> = listOf(
    "example.com" to "www.example.com",
    "www.example.com" to "example.com",
    "news.example.com" to "www.news.example.com",
    "www.news.example.com" to "news.example.com",
    "www.www.example.com" to "www.example.com",
    "xn--bcher-kva.example" to "www.xn--bcher-kva.example",
)

internal val WWW_WITHOUT_COUNTERPART: List<String> = listOf("www.com")

private fun assertSuccess(result: ExactDomainInputResult): ExactDomain {
    return assertIs<ExactDomainInputResult.Success>(result).domain
}

private fun assertFailure(result: ExactDomainInputResult): ExactDomainInputResult.Failure {
    return assertIs<ExactDomainInputResult.Failure>(result)
}
