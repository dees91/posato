package app.posato.linux.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HelperRequestTest {
    @Test
    fun `given a well formed apply when parsed then its session, end, hosts, and executables are kept`() {
        val request = assertIs<HelperRequest.Apply>(
            HelperRequest.parse("apply\tsession-1\t1760000000000\texample.org,xn--bcher-kva.example\t/usr/bin/foo\u001f/snap/firefox/"),
        )

        assertEquals("session-1", request.sessionId)
        assertEquals(1_760_000_000_000L, request.endEpochMillis)
        assertEquals(listOf("example.org", "xn--bcher-kva.example"), request.hosts)
        assertEquals(listOf("/usr/bin/foo", "/snap/firefox/"), request.executables)
    }

    @Test
    fun `given a host that could inject a line or a mapping when parsed then the whole request is refused`() {
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\n1.2.3.4 bank.example\t"))
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org 1.2.3.4\t"))
        assertNull(HelperRequest.parse("apply\ts\t1\tEXAMPLE.org\t"))
        assertNull(HelperRequest.parse("apply\ts\t1\t-bad.example\t"))
        assertNull(HelperRequest.parse("apply\ts\t1\t${"a".repeat(254)}\t"))
    }

    @Test
    fun `given a directory matcher other than one snap's directory when parsed then it is refused`() {
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\t/"))
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\t/usr/"))
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\t/snap/"))
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\t/snap/firefox/current/"))
    }

    @Test
    fun `given too many hosts or a relative or controlled executable path when parsed then it is refused`() {
        val many = (0..4_096).joinToString(",") { "h$it.example" }
        assertNull(HelperRequest.parse("apply\ts\t1\t$many\t"))
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\tusr/bin/foo"))
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\t/usr/bin/fo\u0007o"))
        assertNull(HelperRequest.parse("apply\ts\tsoon\texample.org\t"))
        assertNull(HelperRequest.parse("apply\t\t1\texample.org\t"))
    }

    @Test
    fun `given clear status and acknowledge when parsed then they are recognized and unknown commands are not`() {
        assertEquals(HelperRequest.Clear, HelperRequest.parse("clear"))
        assertEquals(HelperRequest.Status, HelperRequest.parse("status"))
        assertEquals(HelperRequest.Acknowledge("session-1"), HelperRequest.parse("ack\tsession-1"))
        assertNull(HelperRequest.parse("shell\trm -rf /"))
    }
}
