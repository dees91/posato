package app.posato.desktop.macos

import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MacOsSystemSettingsTest {
    @Test
    fun `given the login items link when opening then the system open command receives it instead of a browser`() {
        val link = URI("x-apple.systempreferences:com.apple.LoginItems-Settings.extension")

        assertEquals(listOf("/usr/bin/open", link.toString()), MacOsSystemSettings.openCommand(link))
    }

    @Test
    fun `given a web link when opening settings then it is refused`() {
        assertFailsWith<IOException> {
            MacOsSystemSettings.openCommand(URI("https://example.com"))
        }
    }
}
