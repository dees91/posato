package app.posato.desktop.linux.status

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusMenuTest {
    private var now = 0L
    private val menu = StatusMenu({ emptyList() }, onAction = {}, onOpened = {}, clock = { now })

    @Test
    fun `given the panel opened the menu a while ago and never said it closed when the layout changes then nothing is sent`() {
        menu.aboutToShow(0)
        now += 10 * 60_000L

        assertNull(menu.layoutChanged())
        assertTrue(menu.aboutToShow(0))
    }

    @Test
    fun `given the menu is open when the layout changes then the panel hears about it at once`() {
        menu.aboutToShow(0)
        now += 5_000L

        assertNotNull(menu.layoutChanged())
        assertFalse(menu.aboutToShow(0))
    }
}
