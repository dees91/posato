package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertFalse
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
}
