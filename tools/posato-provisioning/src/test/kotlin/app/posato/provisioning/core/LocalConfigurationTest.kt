package app.posato.provisioning.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalConfigurationTest {
    @Test
    fun `parses assignments comments and colon separators`() {
        val parsed = LocalConfiguration.parseProperties(
            listOf(
                "# a comment",
                "! another comment",
                "",
                "posato.asc.keyId=ABC123",
                "posato.asc.issuerId : 11112222-3333-4444-5555-666677778888",
                "=novalue",
            ),
        )

        assertEquals("ABC123", parsed["posato.asc.keyId"])
        assertEquals("11112222-3333-4444-5555-666677778888", parsed["posato.asc.issuerId"])
        assertEquals(2, parsed.size)
    }

    @Test
    fun `environment wins over local properties and blanks count as absent`() {
        val configuration = LocalConfiguration(
            properties = mapOf("posato.asc.keyId" to "fromFile", "posato.asc.issuerId" to "   "),
            environment = mapOf("POSATO_ASC_KEY_ID" to "fromEnvironment"),
        )

        assertEquals("fromEnvironment", configuration.value(ConfigurationKey.ASC_KEY_ID))
        assertEquals(ConfigurationSource.ENVIRONMENT, configuration.source(ConfigurationKey.ASC_KEY_ID))
        assertNull(configuration.value(ConfigurationKey.ASC_ISSUER_ID))
        assertEquals(ConfigurationSource.ABSENT, configuration.source(ConfigurationKey.ASC_ISSUER_ID))
    }

    @Test
    fun `require names both the property and the environment variable without a value`() {
        val configuration = LocalConfiguration(emptyMap(), emptyMap())

        val failure = assertFailsWith<ProvisioningException> {
            configuration.require(ConfigurationKey.ASC_PRIVATE_KEY_PATH, "Signing a token")
        }

        assertEquals(ErrorCode.CONFIGURATION_MISSING, failure.code)
        assertEquals(true, failure.message?.contains("posato.asc.privateKeyPath"))
        assertEquals(true, failure.hint?.contains("POSATO_ASC_PRIVATE_KEY_PATH"))
    }
}
