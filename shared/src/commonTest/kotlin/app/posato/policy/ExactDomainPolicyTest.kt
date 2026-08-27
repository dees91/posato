package app.posato.policy

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
}

private fun assertSuccess(result: ExactDomainInputResult): ExactDomain {
    return assertIs<ExactDomainInputResult.Success>(result).domain
}

private fun assertFailure(result: ExactDomainInputResult): ExactDomainInputResult.Failure {
    return assertIs<ExactDomainInputResult.Failure>(result)
}
