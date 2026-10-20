package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `vm create` claims a lease from the host's machine-ledger before it clones. Failure modes: a claim that machine-ledger
 * reads as another artifact or without the allowed shortage, a refusal below the reserve that still clones, and a host
 * without machine-ledger, or with one that fails, that can no longer create a clone. E2E runs on one host whose
 * ledger is installed and admits the claim, so it reaches none of these.
 */
class MachineLedgerTest {
    private val binary = Path.of("/opt/bin/machine-ledger")
    private val cloneDirectory = Path.of("/tart/vms/posato-run-primary")
    private val worktree = Path.of("/work/posato")
    private val commands = mutableListOf<List<String>>()
    private val logged = mutableListOf<String>()

    private fun ledger(
        binary: Path? = this.binary,
        exitCode: Int = 0,
        stderr: String = "",
    ) = MachineLedger(binary, logged::add) { command ->
        commands += command
        ProcessOutput(command, exitCode, "claimed tart:posato-run-primary\n", stderr)
    }

    private fun List<String>.option(name: String): String? = indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

    @Test
    fun `a claim names the clone as a Posato Tart VM with its directory, expected growth, and creating worktree`() {
        ledger().claim("posato-run-primary", cloneDirectory, worktree, belowReserve = false)

        val command = commands.single()
        assertEquals(listOf(binary.toString(), "claim"), command.take(2))
        assertEquals("Polyglot/posato", command.option("--owner"))
        assertEquals("tart-vm", command.option("--kind"))
        assertEquals("tart:posato-run-primary", command.option("--id"))
        assertEquals(cloneDirectory.toString(), command.option("--path"))
        assertEquals("5G", command.option("--size"))
        assertEquals("24h", command.option("--ttl"))
        assertTrue(command.option("--note").orEmpty().contains(worktree.toString()))
        assertFalse("--below-reserve" in command)
    }

    @Test
    fun `a clone allowed below the disk minimum is claimed below the reserve`() {
        ledger().claim("posato-run-primary", cloneDirectory, worktree, belowReserve = true)

        assertTrue("--below-reserve" in commands.single())
    }

    @Test
    fun `a claim refused below the reserve stops the clone with machine-ledger's reason`() {
        val reason = "machine-ledger: claim refused for tart:posato-run-primary (Polyglot/posato): free 22.0 GiB"

        val failure = assertFailsWith<ControlException> {
            ledger(exitCode = 3, stderr = "$reason\n").claim("posato-run-primary", cloneDirectory, worktree, belowReserve = false)
        }

        assertEquals(ErrorCode.DISK_SPACE_LOW, failure.code)
        assertTrue(failure.message.orEmpty().contains(reason))
        assertTrue(failure.hint.orEmpty().contains("--allow-low-disk"))
    }

    @Test
    fun `without machine-ledger the clone goes ahead without a lease`() {
        val ledger = ledger(binary = null)

        ledger.claim("posato-run-primary", cloneDirectory, worktree, belowReserve = false)
        ledger.release("posato-run-primary")

        assertTrue(commands.isEmpty())
        assertEquals(1, logged.size)
    }

    @Test
    fun `a machine-ledger that fails or does not finish never stops the clone or its removal`() {
        ledger(exitCode = 2, stderr = "machine-ledger claim: not a size").claim("posato-run-primary", cloneDirectory, worktree, false)
        val stuck = MachineLedger(binary, logged::add) { throw ControlException(ErrorCode.COMMAND_FAILED, "did not finish within 60 s.") }
        stuck.claim("posato-run-primary", cloneDirectory, worktree, belowReserve = false)
        stuck.release("posato-run-primary")

        assertEquals(3, logged.size)
    }

    @Test
    fun `a release ends the clone's lease by its id`() {
        ledger().release("posato-run-peer")

        assertEquals(listOf(binary.toString(), "release", "tart:posato-run-peer"), commands.single())
    }

    @Test
    fun `machine-ledger is found in the user's local bin when PATH lacks it, and is absent otherwise`() {
        val home = createTempDirectory("posato-control-home")
        try {
            val elsewhere = Files.createDirectories(home.resolve("elsewhere")).toString()
            assertNull(locateMachineLedger(elsewhere, home))

            val installed = Files.createDirectories(home.resolve(".local/bin")).resolve("machine-ledger")
            Files.writeString(installed, "#!/bin/sh\n")
            installed.toFile().setExecutable(true)

            assertEquals(installed, locateMachineLedger(elsewhere, home))
        } finally {
            home.toFile().deleteRecursively()
        }
    }
}
