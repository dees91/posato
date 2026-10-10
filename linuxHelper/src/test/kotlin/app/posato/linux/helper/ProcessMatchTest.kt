package app.posato.linux.helper

import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessMatchTest {
    private val matchers = listOf("/usr/lib/foo/foo", "/snap/firefox/")

    @Test
    fun `given the person's process running a chosen executable or snap when checked then it is ended`() {
        assertEquals(true, ProcessMatch.shouldEnd("/usr/lib/foo/foo", ownerUid = 1000, personUid = 1000, matchers))
        assertEquals(true, ProcessMatch.shouldEnd("/snap/firefox/5123/usr/lib/firefox/firefox", 1000, 1000, matchers))
    }

    @Test
    fun `given a process of root or another user or another program when checked then it is left alone`() {
        assertEquals(false, ProcessMatch.shouldEnd("/usr/lib/foo/foo", ownerUid = 0, personUid = 1000, matchers))
        assertEquals(false, ProcessMatch.shouldEnd("/usr/lib/foo/foo", ownerUid = 1001, personUid = 1000, matchers))
        assertEquals(false, ProcessMatch.shouldEnd("/usr/lib/foo/foobar", 1000, 1000, matchers))
        assertEquals(false, ProcessMatch.shouldEnd("/snap/firefoxy/1/x", 1000, 1000, matchers))
    }
}
