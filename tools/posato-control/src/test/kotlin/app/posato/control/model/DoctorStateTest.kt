package app.posato.control.model

import app.posato.control.core.ControlJson
import app.posato.control.desktop.SyncProfile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoctorStateTest {
    @Test
    fun `the three states carry the documented ok and severity`() {
        val pass = DoctorCheck.pass("a", "detail")
        assertEquals("ok", pass.state)
        assertTrue(pass.ok)
        assertEquals("info", pass.severity)

        val missing = DoctorCheck.fail("b", "detail", "remedy")
        assertEquals("missing", missing.state)
        assertFalse(missing.ok)
        assertEquals("error", missing.severity)

        val unknown = DoctorCheck.unknown("c", "detail", "remedy")
        assertEquals("unknown", unknown.state)
        assertFalse(unknown.ok)
        assertEquals("warn", unknown.severity)
    }

    @Test
    fun `the check state reaches the JSON envelope`() {
        val json = ControlJson.pretty.encodeToString(
            DoctorReport.serializer(),
            DoctorReport(true, listOf(DoctorCheck.unknown("c", "detail", "remedy"))),
        )
        assertTrue(json.contains("\"state\": \"unknown\""), json)
    }
}

class SyncProfileDecodeTest {
    private val plist =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <plist version="1.0"><dict>
          <key>Entitlements</key>
          <dict>
            <key>com.apple.application-identifier</key>
            <string>AAAAAAAAAA.app.posato.macos.sync</string>
          </dict>
          <key>ExpirationDate</key>
          <date>2027-09-04T09:55:19Z</date>
          <key>TeamIdentifier</key>
          <array>
            <string>AAAAAAAAAA</string>
          </array>
        </dict></plist>
        """.trimIndent()

    @Test
    fun `a decoded profile exposes the fields packaging depends on`() {
        val profile = requireNotNull(SyncProfile.decode(plist))
        assertEquals("AAAAAAAAAA.app.posato.macos.sync", profile.applicationIdentifier)
        assertEquals("AAAAAAAAAA", profile.teamIdentifier)
        assertEquals(Instant.parse("2027-09-04T09:55:19Z"), profile.expiresAt)
    }

    @Test
    fun `output that is not a provisioning profile decodes to nothing`() {
        assertNull(SyncProfile.decode("not a profile"))
    }
}
