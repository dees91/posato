package app.posato.desktop.linux.status

import kotlin.test.Test
import kotlin.test.assertFalse

class LinuxStatusAreaTest {
    @Test
    fun `given no session bus when Posato starts then it runs without an icon instead of failing`() {
        val area = LinuxStatusArea(
            StatusIcons("/nonexistent"),
            onShow = {},
            onAction = {},
            onMenuOpened = {},
            onBusLost = {},
            sessionBusAddress = { "" },
        )

        assertFalse(area.start())
        assertFalse(area.watcherPresent.value)
        assertFalse(LinuxStatusArea.forwardShow("", sessionBusAddress = { null }))
    }
}
