package app.posato.windows.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HelperRequestTest {
    @Test
    fun `given a well formed apply when parsed then its session, end, hosts, and executables are kept`() {
        val request = assertIs<HelperRequest.Apply>(
            HelperRequest.parse(
                "apply\tsession-1\t1760000000000\texample.org,xn--bcher-kva.example\t" +
                    "C:\\Program Files\\Foo\\foo.exe\u001fD:\\Games\\Bar Game\\BAR.EXE",
            ),
        )

        assertEquals("session-1", request.sessionId)
        assertEquals(1_760_000_000_000L, request.endEpochMillis)
        assertEquals(listOf("example.org", "xn--bcher-kva.example"), request.hosts)
        assertEquals(listOf("C:\\Program Files\\Foo\\foo.exe", "D:\\Games\\Bar Game\\BAR.EXE"), request.executables)
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
    fun `given an executable that is not one absolute local exe path when parsed then the whole request is refused`() {
        listOf(
            "foo.exe",
            "Program Files\\Foo\\foo.exe",
            "\\Windows\\foo.exe",
            "C:foo.exe",
            "C:/Program Files/Foo/foo.exe",
            "\\\\server\\share\\foo.exe",
            "\\\\?\\C:\\Foo\\foo.exe",
            "\\\\.\\pipe\\foo.exe",
            "C:\\Program Files\\Foo\\..\\..\\Windows\\System32\\cmd.exe",
            "C:\\Program Files\\Foo\\.\\foo.exe",
            "C:\\Program Files\\Foo\\\\foo.exe",
            "C:\\Program Files\\Foo\\foo.bat",
            "C:\\Program Files\\Foo\\foo.exe\\",
            "C:\\Program Files\\Foo\\fo\u0007o.exe",
            "C:\\Program Files\\Foo\\foo.exe:stream.exe",
            "C:\\Program Files\\Foo\\foo?.exe",
            "C:\\Program Files\\Foo\\foo*.exe",
            "C:\\Program Files\\Foo\\<foo>.exe",
            "C:\\Program Files\\Foo\\\"foo\".exe",
            "C:\\Program Files\\Foo\\foo|.exe",
            "C:\\" + "a\\".repeat(2_100) + "foo.exe",
        ).forEach { path -> assertNull(HelperRequest.parse("apply\ts\t1\texample.org\t$path"), path) }
    }

    @Test
    fun `given more than 64 executables or too many hosts when parsed then it is refused`() {
        val executables = (0..64).joinToString("\u001f") { "C:\\Apps\\app$it.exe" }
        assertNull(HelperRequest.parse("apply\ts\t1\texample.org\t$executables"))
        val fine = (0 until 64).joinToString("\u001f") { "C:\\Apps\\app$it.exe" }
        assertIs<HelperRequest.Apply>(HelperRequest.parse("apply\ts\t1\texample.org\t$fine"))
        val many = (0..4_096).joinToString(",") { "h$it.example" }
        assertNull(HelperRequest.parse("apply\ts\t1\t$many\t"))
        assertNull(HelperRequest.parse("apply\ts\tsoon\texample.org\t"))
        assertNull(HelperRequest.parse("apply\t\t1\texample.org\t"))
    }

    @Test
    fun `given clear status and acknowledge when parsed then they are recognized and unknown commands are not`() {
        assertEquals(HelperRequest.Clear, HelperRequest.parse("clear"))
        assertEquals(HelperRequest.Status, HelperRequest.parse("status"))
        assertEquals(HelperRequest.Acknowledge("session-1"), HelperRequest.parse("ack\tsession-1"))
        assertNull(HelperRequest.parse("shell\tcmd /c del C:\\"))
        assertNull(HelperRequest.parse("clear\textra"))
    }
}
