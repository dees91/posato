package app.posato.linux.helper

import java.nio.file.Files
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The root service follows the plans of the last apply on its own one-second tick, with the application gone: a
 * part that ends with no new apply opens the surviving part's window, a clock jump such as a sleep is evaluated on
 * the next tick, a time zone change applies without a restart, and a restart keeps the plans (ADR 0010 amendment).
 */
class HelperServiceWindowTest {
    private val directory = Files.createTempDirectory("posato-helper-window")
    private val paths = HelperPaths(
        socket = directory.resolve("helper.sock"),
        state = directory.resolve("state"),
        hosts = directory.resolve("hosts"),
        config = directory.resolve("helper.conf"),
    )
    private var now = AT_1000
    private var zone: ZoneId = ZoneId.of("UTC")

    init {
        Files.writeString(paths.hosts, "127.0.0.1 localhost\n")
    }

    @AfterTest
    fun removeDirectory() {
        directory.toFile().deleteRecursively()
    }

    private fun service() = HelperService(paths, Person("tester", 1_000), now = { now }, zone = { zone })

    /** The names the hosts file sends nowhere. */
    private fun blockedNames(): Set<String> {
        return Files.readAllLines(paths.hosts).filter { it.endsWith(" # posato") }.map { it.split(' ')[1] }.toSet()
    }

    private fun applyOverlap(service: HelperService) {
        // An unruled manual part ends at 10:10; a scheduled part with a 30/5 rule runs to 11:00.
        val line = "apply\tsession-1\t$AT_1100\texample.com,example.org\t\texample.com=$AT_1010:0:0;$AT_1100:30:5"
        assertEquals("ok", service.handle(HelperRequest.parse(line)!!))
    }

    @Test
    fun `given a part that ends with no new apply when the tick reaches the surviving window then only the ruled host opens`() {
        val service = service()
        applyOverlap(service)
        val all = setOf("example.com", "www.example.com", "example.org", "www.example.org")
        assertEquals(all, blockedNames())

        now = AT_1010
        service.tick()
        assertEquals(all, blockedNames())

        now = AT_1030 - SECOND
        service.tick()
        assertEquals(all, blockedNames())

        now = AT_1030 - 1
        service.tick()
        assertEquals(all, blockedNames())

        now = AT_1030
        service.tick()
        assertEquals(setOf("example.org", "www.example.org"), blockedNames())

        now = AT_1030 + 5 * MINUTE
        service.tick()
        assertEquals(all, blockedNames())
    }

    @Test
    fun `given an apply inside an open window when applied then the ruled host is left out at once`() {
        now = AT_1030 + MINUTE
        val service = service()
        applyOverlap(service)

        assertEquals(setOf("example.org", "www.example.org"), blockedNames())
    }

    @Test
    fun `given a clock jump over a whole window when the next tick runs then the state of the new instant applies`() {
        val service = service()
        applyOverlap(service)

        now = AT_1030 + 2 * MINUTE
        service.tick()
        assertEquals(setOf("example.org", "www.example.org"), blockedNames())

        now = AT_1030 + 20 * MINUTE
        service.tick()
        assertEquals(setOf("example.com", "www.example.com", "example.org", "www.example.org"), blockedNames())
    }

    @Test
    fun `given a time zone change when the next tick runs then the wall clock of the new zone decides without a restart`() {
        val service = service()
        applyOverlap(service)
        now = AT_1030 + 2 * MINUTE
        service.tick()
        assertEquals(setOf("example.org", "www.example.org"), blockedNames())

        // Kolkata is UTC+05:30: 10:32 UTC is 16:02 there, inside its own 16:00 window; Kathmandu (+05:45) is 16:17.
        zone = ZoneId.of("Asia/Kathmandu")
        service.tick()
        assertEquals(setOf("example.com", "www.example.com", "example.org", "www.example.org"), blockedNames())

        zone = ZoneId.of("Asia/Kolkata")
        service.tick()
        assertEquals(setOf("example.org", "www.example.org"), blockedNames())
    }

    @Test
    fun `given a restart of the service when it ticks again then the plans of the saved apply still open the window`() {
        applyOverlap(service())

        now = AT_1030
        service().tick()

        assertEquals(setOf("example.org", "www.example.org"), blockedNames())
    }

    @Test
    fun `given the end of the request when the tick runs then every host is released and the expiry is kept`() {
        val service = service()
        applyOverlap(service)

        now = AT_1100
        service.tick()

        assertEquals(emptySet(), blockedNames())
        assertEquals("ok\t-\tsession-1", service.handle(HelperRequest.Status))
    }

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60_000L
        const val AT_1000 = 1_793_613_600_000L
        const val AT_1010 = AT_1000 + 10 * MINUTE
        const val AT_1030 = AT_1000 + 30 * MINUTE
        const val AT_1100 = AT_1000 + 60 * MINUTE
    }
}
