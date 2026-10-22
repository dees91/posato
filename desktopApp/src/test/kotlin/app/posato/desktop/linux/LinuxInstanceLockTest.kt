package app.posato.desktop.linux

import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxInstanceLockTest {
    private val directory: Path = Files.createTempDirectory("posato-linux-lock")
    private val path: Path = directory.resolve("instance.lock")
    private val probes = mutableListOf<Process>()

    @AfterTest
    fun cleanUp() {
        probes.forEach { probe -> probe.destroyForcibly().waitFor(5, TimeUnit.SECONDS) }
        path.deleteIfExists()
        directory.deleteIfExists()
    }

    @Test
    fun `given another process holds the lock and its bus name is unreachable when Posato starts then it exits without the database`() {
        startProbe().expect("held")

        val decision = linuxStartup(LinuxInstanceLock(path), forwardShow = { false })

        assertEquals(LinuxStartup.EXIT, decision)
    }

    @Test
    fun `given another process holds the lock when the running one shows its window then this launch only forwards`() {
        startProbe().expect("held")

        assertEquals(LinuxStartup.FORWARDED, linuxStartup(LinuxInstanceLock(path), forwardShow = { true }))
    }

    @Test
    fun `given the holder quits when Posato starts then it runs`() {
        val holder = startProbe().also { it.expect("held") }
        holder.outputStream.write('\n'.code)
        holder.outputStream.flush()
        assertTrue(holder.waitFor(10, TimeUnit.SECONDS))

        assertEquals(LinuxStartup.RUN, linuxStartup(LinuxInstanceLock(path), forwardShow = { error("not called") }))
    }

    private fun startProbe(): Process {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val probe = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), LinuxInstanceLockProbeMain::class.java.name, path.toString())
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
