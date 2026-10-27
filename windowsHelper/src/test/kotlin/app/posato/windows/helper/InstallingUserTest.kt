package app.posato.windows.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstallingUserTest {
    private val user = "S-1-5-21-1004336348-1177238915-682003330-1001"

    @Test
    fun `given the installer's logon name when qualified then a local name gains the computer name and a qualified one is kept`() {
        assertEquals("POSATO-PC\\alice", InstallingUser.qualify("alice", "POSATO-PC"))
        assertEquals("CONTOSO\\alice", InstallingUser.qualify("CONTOSO\\alice", "POSATO-PC"))
        assertNull(InstallingUser.qualify("", "POSATO-PC"))
        assertNull(InstallingUser.qualify("ali\nce", "POSATO-PC"))
        assertNull(InstallingUser.qualify("a\\b\\c", "POSATO-PC"))
    }

    @Test
    fun `given a person's account when checked then it can be served and the system or a service account cannot`() {
        assertEquals(true, InstallingUser.isPerson(user))
        assertEquals(true, InstallingUser.isPerson("S-1-12-1-1234567890-1234567890-1234567890-1234567890"))
        listOf(
            "S-1-5-18",
            "S-1-5-19",
            "S-1-5-20",
            "S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464",
            "S-1-5-90-0-1",
            "S-1-5-96-0-1",
            "S-1-5-32-544",
            "S-1-5-21-1004336348-1177238915-682003330",
            "S-1-5-21-x-1001",
            "",
        ).forEach { sid -> assertEquals(false, InstallingUser.isPerson(sid), sid) }
    }

    @Test
    fun `given the recorded configuration when read back then the user is kept and a tampered account is refused`() {
        val config = HelperConfig("POSATO-PC\\alice", user)

        assertEquals(config, HelperConfig.decode(config.encode()))
        assertNull(HelperConfig.decode("user=POSATO-PC\\alice\nsid=S-1-5-18\n"))
        assertNull(HelperConfig.decode("user=POSATO-PC\\alice\n"))
        assertNull(HelperConfig.decode("sid=$user\n"))
    }
}
