package app.posato.provisioning.local

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecodedProfileTest {
    @Test
    fun `reads the fields that decide whether a macOS profile is the right one`() {
        val decoded = DecodedProfile.decode(plist("com.apple.application-identifier", "OSX"))!!

        assertEquals("ABCDE12345.app.posato.macos.sync", decoded.applicationIdentifier)
        assertEquals("ABCDE12345", decoded.teamIdentifier)
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), decoded.expiresAt)
        assertEquals("11112222-3333-4444-5555-666677778888", decoded.uuid)
        assertEquals(listOf("OSX"), decoded.platforms)
    }

    @Test
    fun `reads the unprefixed application identifier an iOS profile carries`() {
        val decoded = DecodedProfile.decode(plist("application-identifier", "iOS"))!!

        assertEquals("ABCDE12345.app.posato.macos.sync", decoded.applicationIdentifier)
        assertEquals(listOf("iOS"), decoded.platforms)
    }

    @Test
    fun `reports nothing for output that is not a profile`() {
        assertNull(DecodedProfile.decode("<plist><dict></dict></plist>"))
    }

    private fun plist(
        identifierKey: String,
        platform: String
    ): String =
        """
        <plist><dict>
          <key>UUID</key><string>11112222-3333-4444-5555-666677778888</string>
          <key>TeamIdentifier</key><array><string>ABCDE12345</string></array>
          <key>ExpirationDate</key><date>2027-01-01T00:00:00Z</date>
          <key>Platform</key><array><string>$platform</string></array>
          <key>Entitlements</key><dict>
            <key>$identifierKey</key><string>ABCDE12345.app.posato.macos.sync</string>
          </dict>
        </dict></plist>
        """.trimIndent()
}
