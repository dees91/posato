package app.posato.windows.helper

import kotlin.test.Test
import kotlin.test.assertEquals

class PipeCallerTest {
    private val user = "S-1-5-21-1004336348-1177238915-682003330-1001"

    @Test
    fun `given the installing user or the system when a request arrives then it is served`() {
        assertEquals(true, PipeCaller.isAllowed(user, user))
        assertEquals(true, PipeCaller.isAllowed("S-1-5-18", user))
    }

    @Test
    fun `given any other caller or an unknown one when a request arrives then it is refused`() {
        listOf(
            null,
            "",
            "S-1-5-21-1004336348-1177238915-682003330-1002",
            "S-1-5-21-1004336348-1177238915-682003330-500",
            "S-1-5-19",
            "S-1-5-20",
            "S-1-5-32-544",
            "S-1-1-0",
            "S-1-5-7",
            "$user-1",
        ).forEach { caller -> assertEquals(false, PipeCaller.isAllowed(caller, user), caller) }
    }
}
