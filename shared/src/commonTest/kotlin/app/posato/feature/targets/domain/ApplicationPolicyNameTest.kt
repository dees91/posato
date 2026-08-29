package app.posato.feature.targets.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ApplicationPolicyNameTest {
    @Test
    fun `given whitespace and decomposed unicode when parsed then a trimmed NFC name is returned`() {
        val result = ApplicationPolicyName.parse("  Cafe\u0301  ")

        assertEquals("Caf\u00E9", assertSuccess(result).canonicalValue)
    }

    @Test
    fun `given valid UTF-8 boundaries when parsed then one and eighty bytes are accepted`() {
        assertEquals("a", assertSuccess(ApplicationPolicyName.parse("a")).canonicalValue)
        assertEquals("é".repeat(40), assertSuccess(ApplicationPolicyName.parse("é".repeat(40))).canonicalValue)
    }

    @Test
    fun `given oversized input when parsed then raw and UTF-8 limits fail`() {
        assertFailure(ApplicationPolicyName.parse("€".repeat(27)), ApplicationPolicyNameFailure.TOO_LONG)
        assertFailure(
            ApplicationPolicyName.parse(" ".repeat(ApplicationPolicyNameLimits.MAX_RAW_INPUT_LENGTH + 1)),
            ApplicationPolicyNameFailure.TOO_LONG,
        )
        assertFailure(
            ApplicationPolicyName.parse("\u000A" + "a".repeat(ApplicationPolicyNameLimits.MAX_RAW_INPUT_LENGTH)),
            ApplicationPolicyNameFailure.TOO_LONG,
        )
    }

    @Test
    fun `given empty malformed C0 or C1 input when parsed then it fails`() {
        assertFailure(ApplicationPolicyName.parse("   "), ApplicationPolicyNameFailure.EMPTY)
        assertFailure(ApplicationPolicyName.parse("line\u000Afeed"), ApplicationPolicyNameFailure.INVALID_CHARACTERS)
        assertFailure(ApplicationPolicyName.parse("\u000ASocial feeds"), ApplicationPolicyNameFailure.INVALID_CHARACTERS)
        assertFailure(ApplicationPolicyName.parse("Social feeds\u0009"), ApplicationPolicyNameFailure.INVALID_CHARACTERS)
        assertFailure(ApplicationPolicyName.parse("delete\u007Fcharacter"), ApplicationPolicyNameFailure.INVALID_CHARACTERS)
        assertFailure(ApplicationPolicyName.parse("broken\uD800"), ApplicationPolicyNameFailure.INVALID_CHARACTERS)
    }

    @Test
    fun `given stored values when restored then only canonical values are accepted`() {
        assertEquals("Caf\u00E9", ApplicationPolicyName.restore("Caf\u00E9")?.canonicalValue)
        assertNull(ApplicationPolicyName.restore(" Cafe\u0301 "))
        assertNull(ApplicationPolicyName.restore("Cafe\u0301"))
        assertNull(ApplicationPolicyName.restore("line\u0085break"))
    }

    @Test
    fun `given a name when rendered as text then its value remains redacted`() {
        assertEquals("ApplicationPolicyName(redacted)", assertSuccess(ApplicationPolicyName.parse("Private")).toString())
    }
}

private fun assertSuccess(result: ApplicationPolicyNameResult): ApplicationPolicyName {
    return assertIs<ApplicationPolicyNameResult.Success>(result).name
}

private fun assertFailure(
    result: ApplicationPolicyNameResult,
    expected: ApplicationPolicyNameFailure,
) {
    assertEquals(expected, assertIs<ApplicationPolicyNameResult.Failure>(result).reason)
}
