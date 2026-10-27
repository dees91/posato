package app.posato.core.designsystem

import kotlin.test.Test
import kotlin.test.assertNotEquals

class DesktopDeviceTest {
    @Test
    fun `given an unrecognised desktop when detected then it does not call itself a Mac`() {
        assertNotEquals(PosatoDevice.Mac, desktopDevice("FreeBSD"))
    }
}
