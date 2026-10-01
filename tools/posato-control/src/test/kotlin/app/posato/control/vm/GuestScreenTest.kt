package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestScreenTest {
    private fun line(text: String) = RecognizedLine(text, 1.0, 0, 0, 10, 10)

    @Test
    fun `given an exact query when a longer line contains it then the line does not match, so an exact wait for absence ends`() {
        assertFalse(line("Continue with Apple Account").matches("Continue", exact = true))
        assertTrue(line(" Continue ").matches("Continue", exact = true))
    }

    @Test
    fun `given a contains query when a longer line contains it in another case then the line matches`() {
        assertTrue(line("Continue with Apple Account").matches("continue", exact = false))
        assertFalse(line("Cancel").matches("Continue", exact = false))
    }

    @Test
    fun `given the image window when a title and a desktop icon repeat the name then the drag takes the icon beside the target`() {
        val title = RecognizedLine("Posato", 1.0, 680, 50, 80, 24)
        val volume = RecognizedLine("Posato", 1.0, 2700, 220, 90, 24)
        val icon = RecognizedLine("Posato", 1.0, 960, 410, 80, 24)
        val applications = RecognizedLine("/Appligations", 0.6, 1480, 410, 150, 24)

        val endpoints = dragEndpoints(listOf(title, volume, icon, applications), "Posato", "/Applications")

        assertEquals(icon to applications, endpoints)
    }

    @Test
    fun `given a 1x display that misreads the icon label when the drag looks beside the target then it still takes the icon`() {
        val title = RecognizedLine("Posato", 1.0, 610, 30, 60, 14)
        val icon = RecognizedLine("Posato (", 1.0, 479, 198, 42, 10)
        val applications = RecognizedLine("/Applications", 1.0, 736, 196, 77, 15)

        val endpoints = dragEndpoints(listOf(title, icon, applications), "Posato", "/Applications")

        assertEquals(icon to applications, endpoints)
    }

    @Test
    fun `given a label read with more than two errors then the drag finds no target`() {
        val icon = RecognizedLine("Posato", 1.0, 960, 410, 80, 24)

        assertNull(dragEndpoints(listOf(icon, RecognizedLine("Downloads", 1.0, 1480, 410, 150, 24)), "Posato", "/Applications"))
        assertTrue(RecognizedLine("/Appligations", 1.0, 0, 0, 1, 1).resembles("/Applications"))
    }
}
