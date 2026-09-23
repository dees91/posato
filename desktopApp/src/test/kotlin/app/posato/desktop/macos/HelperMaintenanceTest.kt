package app.posato.desktop.macos

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelperMaintenanceTest {
    @Test
    fun `given the backstop when enforcement applies then the helper is not contacted and the answer is a refusal`() {
        val maintenance = HelperMaintenance()
        maintenance.engageBackstop()
        MacOsHelperClient(helperPath = MISSING_HELPER, maintenance = maintenance).use { client ->
            val result = client.apply(PORT)

            assertEquals(HelperResult.Outcome.Failure, result.outcome)
            assertEquals(HelperResult.Failure.Unavailable, result.failure)
        }
    }

    @Test
    fun `given the backstop when domains or applications are configured then non-empty sets are refused without the helper`() {
        val maintenance = HelperMaintenance()
        maintenance.engageBackstop()
        MacOsHelperClient(helperPath = MISSING_HELPER, maintenance = maintenance).use { client ->
            assertEquals(HelperResult.Outcome.Failure, client.configureBrowserDomains(listOf("example.com"), END).result.outcome)
            assertEquals(HelperResult.Outcome.Failure, client.configureApplications(listOf(byteArrayOf(1)), END).result.outcome)
        }
    }

    @Test
    fun `given refused spawns when any helper request needs a process then it fails before starting one`() {
        val maintenance = HelperMaintenance()
        assertTrue(maintenance.refuseSpawnsAndStopHelpers())
        MacOsHelperClient(helperPath = MISSING_HELPER, maintenance = maintenance).use { client ->
            assertFailsWith<IllegalStateException> { client.status() }
            assertFailsWith<IllegalStateException> { client.enable() }
            assertEquals(MacOsApplicationPickerResult.Failure, client.selectApplications())
        }
    }

    @Test
    fun `given a running helper when spawns are refused then it is stopped and confirmed exited`() {
        val maintenance = HelperMaintenance()
        val helper = maintenance.spawn { ProcessBuilder("/bin/sleep", "30").start() }

        assertTrue(maintenance.refuseSpawnsAndStopHelpers())
        assertFalse(helper.isAlive)
    }

    @Test
    fun `given refused spawns when a spawn is attempted then no process starts`() {
        val maintenance = HelperMaintenance()
        maintenance.refuseSpawnsAndStopHelpers()
        var started = false

        assertFailsWith<IllegalStateException> {
            maintenance.spawn {
                started = true
                ProcessBuilder("/usr/bin/true").start()
            }
        }
        assertFalse(started)
    }

    @Test
    fun `given maintenance ended when spawning then a helper can start again while the backstop stays until released`() {
        val maintenance = HelperMaintenance()
        maintenance.engageBackstop()
        maintenance.refuseSpawnsAndStopHelpers()

        maintenance.allowSpawns()
        val process = maintenance.spawn { ProcessBuilder("/usr/bin/true").start() }
        process.waitFor()

        assertTrue(maintenance.isBackstopEngaged)
        maintenance.releaseBackstop()
        assertFalse(maintenance.isBackstopEngaged)
    }

    private companion object {
        val MISSING_HELPER: Path = Path.of("/nonexistent/PosatoMacOSHelper")
        val PORT: UShort = 8080u
        const val END: Long = 1_700_000_000_000L
    }
}
