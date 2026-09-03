package app.posato.control.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalConfigurationTest {
    private val properties = LocalConfiguration.parseProperties(
        listOf(
            "# comment",
            "sdk.dir=/example/sdk",
            "posato.apple.developmentTeam = TEAM000000 ",
            "posato.control.simulator:iPhone 17",
            "broken line without separator",
        ),
    )

    @Test
    fun `parses keys with equals or colon and trims values`() {
        assertEquals("TEAM000000", properties["posato.apple.developmentTeam"])
        assertEquals("iPhone 17", properties["posato.control.simulator"])
        assertEquals(3, properties.size)
    }

    @Test
    fun `environment overrides local properties and reports its source`() {
        val configuration = LocalConfiguration(properties, mapOf("POSATO_APPLE_DEVELOPMENT_TEAM" to "ENV0000000"))
        assertEquals("ENV0000000", configuration.value(ConfigurationKey.DEVELOPMENT_TEAM))
        assertEquals(ConfigurationSource.ENVIRONMENT, configuration.source(ConfigurationKey.DEVELOPMENT_TEAM))
        assertEquals(ConfigurationSource.LOCAL_PROPERTIES, configuration.source(ConfigurationKey.SIMULATOR))
        assertEquals(ConfigurationSource.ABSENT, configuration.source(ConfigurationKey.DEVICE))
        assertNull(configuration.value(ConfigurationKey.DEVICE))
    }

    @Test
    fun `missing required values name the key and the hint`() {
        val configuration = LocalConfiguration(emptyMap(), emptyMap())
        val failure = assertFailsWith<ControlException> {
            configuration.require(ConfigurationKey.DEVELOPMENT_TEAM, ErrorCode.DEVELOPMENT_TEAM_MISSING, "Signing")
        }
        assertEquals(ErrorCode.DEVELOPMENT_TEAM_MISSING, failure.code)
        assertEquals(true, failure.hint?.contains("posato.apple.developmentTeam"))
    }
}
