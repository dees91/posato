package app.posato.linux.helper

import kotlin.test.Test
import kotlin.test.assertEquals

class HostsBlockTest {
    private val system = "127.0.0.1 localhost\n::1 ip6-localhost\n# a person's own line\n10.0.0.2 nas.home\n"

    @Test
    fun `given hosts when applied then each host and its www counterpart map to both unspecified addresses after the person's lines`() {
        val written = HostsBlock.rewrite(system, listOf("example.org"))

        assertEquals(
            system + "# BEGIN POSATO\n" +
                "0.0.0.0 example.org # posato\n:: example.org # posato\n" +
                "0.0.0.0 www.example.org # posato\n:: www.example.org # posato\n" +
                "# END POSATO\n",
            written,
        )
    }

    @Test
    fun `given an applied block when cleared then the file is exactly what the person had`() {
        assertEquals(system, HostsBlock.rewrite(HostsBlock.rewrite(system, listOf("example.org", "news.example")), emptyList()))
    }

    @Test
    fun `given a duplicated block and an unterminated one when rewritten then every Posato line goes and nothing else`() {
        val damaged = system + "# BEGIN POSATO\n0.0.0.0 a.example # posato\n# END POSATO\n" +
            "# BEGIN POSATO\n0.0.0.0 b.example # posato\n" + "192.168.1.9 printer\n"

        assertEquals(system + "192.168.1.9 printer\n", HostsBlock.rewrite(damaged, emptyList()))
    }

    @Test
    fun `given a host already starting with www when applied then it is written once`() {
        val written = HostsBlock.rewrite("", listOf("www.example.org"))

        assertEquals(
            "# BEGIN POSATO\n0.0.0.0 www.example.org # posato\n:: www.example.org # posato\n# END POSATO\n",
            written,
        )
    }
}
