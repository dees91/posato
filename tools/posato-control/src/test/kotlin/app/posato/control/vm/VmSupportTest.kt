package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VmSupportTest {
    private val list =
        """
        [{"Source":"local","State":"stopped","Name":"golden-a","Running":false,"Disk":60,"Size":27},
         {"Source":"local","State":"running","Name":"peer-a","Running":true,"Disk":60,"Size":27}]
        """.trimIndent()

    @Test
    fun `tart list output is read into names and running states`() {
        assertEquals(listOf(TartVm("golden-a", running = false), TartVm("peer-a", running = true)), parseTartList(list))
    }

    @Test
    fun `a clone may start while its golden VM is stopped and one guest runs`() {
        refuseClone(parseTartList(list), golden = "golden-a", clone = "run-a")
    }

    @Test
    fun `a clone is refused while its golden VM runs, because one of the two would lose its provisioning identity`() {
        val vms = listOf(TartVm("golden-a", running = true))

        val failure = assertFailsWith<ControlException> { refuseClone(vms, golden = "golden-a", clone = "run-a") }

        assertEquals(ErrorCode.VM_UNAVAILABLE, failure.code)
    }

    @Test
    fun `a clone is refused when two guests already run`() {
        val vms = listOf(TartVm("x", running = true), TartVm("y", running = true), TartVm("golden-a", running = false))

        assertFailsWith<ControlException> { refuseClone(vms, golden = "golden-a", clone = "run-a") }
    }

    @Test
    fun `a clone is refused when its name is taken or the golden VM is missing`() {
        assertFailsWith<ControlException> { refuseClone(listOf(TartVm("golden-a", false), TartVm("run-a", false)), "golden-a", "run-a") }
        assertFailsWith<ControlException> { refuseClone(emptyList(), "golden-a", "run-a") }
    }

    @Test
    fun `the VNC address is read from the tart run output`() {
        val output = "some warning\nVNC server is running at vnc://:word-word-word-word@127.0.0.1:61453\n"

        assertEquals(VncEndpoint("127.0.0.1", 61453, "word-word-word-word"), parseVncEndpoint(output))
        assertNull(parseVncEndpoint("still booting\n"))
    }

    @Test
    fun `a scenario file travels to the guest on standard input`() {
        val (arguments, file) = scenarioOverStdin(listOf("run", "-t", "desktop", "--scenario", "/host/start.json", "--run-id", "r1"))

        assertEquals(listOf("run", "-t", "desktop", "--scenario", "-", "--run-id", "r1"), arguments)
        assertEquals("/host/start.json", file)
        assertEquals(listOf("snapshot", "-t", "desktop") to null, scenarioOverStdin(listOf("snapshot", "-t", "desktop")))
    }

    @Test
    fun `a scenario already on standard input is forwarded from the host's standard input`() {
        val arguments = listOf("run", "-t", "desktop", "--scenario", "-")

        assertEquals(arguments to STANDARD_INPUT, scenarioOverStdin(arguments))
    }

    @Test
    fun `shell quoting keeps spaces and quotes literal`() {
        assertEquals("'plain'", shellQuote("plain"))
        assertEquals("'My Shared Files'", shellQuote("My Shared Files"))
        assertEquals("'it'\\''s'", shellQuote("it's"))
    }

    @Test
    fun `guest evidence paths in a relayed envelope point at the copied guest directory`() {
        val envelope = """{"artifacts":["build/verification/runs/r1/a.png"],"log":"build/verification/runs/r1/app.log"}"""

        val rewritten = relocateGuestPaths(envelope, runId = "r1")

        assertEquals(
            """{"artifacts":["build/verification/runs/r1/guest/a.png"],"log":"build/verification/runs/r1/guest/app.log"}""",
            rewritten,
        )
    }
}
