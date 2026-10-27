package app.posato.windows.helper

import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessMatchTest {
    private val user = "S-1-5-21-1004336348-1177238915-682003330-1001"
    private val matchers = listOf("C:\\Program Files\\Foo\\foo.exe")

    @Test
    fun `given the user's process running a chosen executable in any letter case when checked then it is ended`() {
        assertEquals(true, ProcessMatch.shouldEnd("C:\\Program Files\\Foo\\foo.exe", user, user, matchers))
        assertEquals(true, ProcessMatch.shouldEnd("c:\\PROGRAM FILES\\foo\\FOO.EXE", user, user, matchers))
    }

    @Test
    fun `given a process of the system, a service account, or another user, or another program when checked then it is left alone`() {
        listOf("S-1-5-18", "S-1-5-19", "S-1-5-20", "S-1-5-21-1004336348-1177238915-682003330-1002").forEach { owner ->
            assertEquals(false, ProcessMatch.shouldEnd("C:\\Program Files\\Foo\\foo.exe", owner, user, matchers), owner)
        }
        assertEquals(false, ProcessMatch.shouldEnd("C:\\Program Files\\Foo\\foo.exe", "S-1-5-18", "S-1-5-18", matchers))
        assertEquals(false, ProcessMatch.shouldEnd("C:\\Program Files\\Foo\\foobar.exe", user, user, matchers))
        assertEquals(false, ProcessMatch.shouldEnd("C:\\Program Files\\Foo\\foo.exe.old", user, user, matchers))
        assertEquals(false, ProcessMatch.shouldEnd("D:\\Program Files\\Foo\\foo.exe", user, user, matchers))
    }
}
