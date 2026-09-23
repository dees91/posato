package app.posato.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals

class SparklePinTest {
    @Test
    fun `given the vendored Sparkle version when the build pins it then the installer label rule matches that version`() {
        assertEquals(System.getProperty("posato.sparkle.version"), PINNED_SPARKLE_VERSION)
        assertEquals("app.posato.macos-sparkle-updater", SPARKLE_INSTALLER_LABEL)
    }
}
