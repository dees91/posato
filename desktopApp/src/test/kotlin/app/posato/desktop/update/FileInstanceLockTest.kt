package app.posato.desktop.update

import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileInstanceLockTest {
    private val directory: Path = Files.createTempDirectory("posato-instance-lock")
    private val path: Path = directory.resolve("instance.lock")
    private val probes = mutableListOf<Process>()

    @AfterTest
    fun cleanUp() {
        probes.forEach { probe -> probe.destroyForcibly().waitFor(5, TimeUnit.SECONDS) }
        path.deleteIfExists()
        directory.deleteIfExists()
    }

    @Test
    fun `given a peer instance when admission upgrades then it is refused and this instance keeps its shared lock`() {
        startProbe("shared").expect("held")
        FileInstanceLock(path).use { lock ->
            assertTrue(lock.acquireShared())

            assertFalse(lock.tryUpgradeForAdmission())
            startProbe("shared").expect("held")
        }
    }

    @Test
    fun `given no peer when admission upgrades then a new instance cannot start until maintenance ends`() {
        FileInstanceLock(path).use { lock ->
            assertTrue(lock.acquireShared())
            assertTrue(lock.tryUpgradeForAdmission())

            startProbe("shared").expect("refused")
            lock.downgradeAfterMaintenance()
            startProbe("shared").expect("held")
        }
    }

    @Test
    fun `given a peer that exited when admission upgrades then it succeeds`() {
        val peer = startProbe("shared")
        peer.expect("held")
        peer.outputStream.close()
        assertTrue(peer.waitFor(10, TimeUnit.SECONDS))
        FileInstanceLock(path).use { lock ->
            assertTrue(lock.acquireShared())

            assertTrue(lock.tryUpgradeForAdmission())
        }
    }

    @Test
    fun `given a peer admitting when this instance starts then it cannot obtain its shared lock`() {
        startProbe("admit").expect("held")
        FileInstanceLock(path).use { lock ->
            assertFalse(lock.acquireShared())
        }
    }

    private fun startProbe(mode: String): Process {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val probe = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), InstanceLockProbeMain::class.java.name, path.toString(), mode)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        probes += probe
        return probe
    }

    private fun Process.expect(line: String) {
        val reader: BufferedReader = inputStream.bufferedReader()
        assertEquals(line, reader.readLine())
    }
}
