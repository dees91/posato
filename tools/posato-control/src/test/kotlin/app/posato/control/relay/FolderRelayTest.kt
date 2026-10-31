package app.posato.control.relay

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The relay must never delete a device's file it could not read. A file another device wrote reaches a Mac guest's
 * iCloud Drive folder as a dataless placeholder whose bytes `tart exec` reads as empty (`SCHEDULE-007`, observed
 * 2026-10-31); deleting it there deleted it in iCloud and on the iPhone that wrote it. E2E shows this only as a bundle
 * that never arrives, so the reconcile rule is pinned here.
 */
class FolderRelayTest {
    private val path = "Posato/bundles/a.pbundle"
    private val bytes = "sealed bundle".encodeToByteArray()

    @Test
    fun `given a listed file whose bytes cannot be read yet when a round runs then it stays on its device`() {
        val author = unreadableAuthor()
        val other = MemoryEndpoint("linux")
        val relay = FolderRelay(Files.createTempDirectory("relay-hub"), listOf(author, other))

        relay.round()
        relay.round()

        assertTrue(path in author.files)
        assertFalse(path in other.files)
    }

    @Test
    fun `given a file that becomes readable when the next round runs then every device receives it`() {
        val author = unreadableAuthor()
        val other = MemoryEndpoint("linux")
        val relay = FolderRelay(Files.createTempDirectory("relay-hub"), listOf(author, other))
        relay.round()

        author.unreadable.clear()
        relay.round()

        assertContentEquals(bytes, other.files[path])
        assertContentEquals(bytes, author.files[path])
    }

    @Test
    fun `given a synchronized file that turns dataless on a Mac guest when a round runs then no device loses it`() {
        val root = Files.createTempDirectory("relay-dataless")
        val evicted = root.resolve("evicted")
        val tools = macTools(root, evicted)
        val guestFolder = Files.createDirectories(root.resolve("guest"))
        val peerFolder = Files.createDirectories(root.resolve("peer"))
        val hub = root.resolve("hub")
        val guest = ShellEndpoint("primary", "'$guestFolder'", localShell(tools))
        val peer = ShellEndpoint("linux", "'$peerFolder'", localShell(null))
        Files.createDirectories(guestFolder.resolve(path).parent)
        Files.write(guestFolder.resolve(path), bytes)
        val relay = FolderRelay(hub, listOf(guest, peer))
        relay.round()

        Files.createFile(evicted)
        relay.round()

        assertContentEquals(bytes, Files.readAllBytes(hub.resolve(path)))
        assertContentEquals(bytes, Files.readAllBytes(peerFolder.resolve(path)))
        assertTrue(Files.exists(guestFolder.resolve(path)))
        assertEquals(0, relay.deletions)
    }

    /** Stands in for the guest's `uname`, `stat` and `brctl`: every file reads as dataless once [evicted] exists, and a download stays pending. */
    private fun macTools(
        root: Path,
        evicted: Path,
    ): Path {
        val tools = Files.createDirectories(root.resolve("tools"))
        val scripts = mapOf(
            "uname" to "echo Darwin",
            "stat" to "if [ -f '$evicted' ]; then echo dataless; else echo -; fi",
            "brctl" to "exit 0",
        )
        scripts.forEach { (name, body) ->
            val tool = tools.resolve(name)
            Files.writeString(tool, "#!/bin/sh\n$body\n")
            tool.toFile().setExecutable(true)
        }
        return tools
    }

    private fun localShell(tools: Path?): DeviceShell {
        return DeviceShell { script, stdin ->
            val builder = ProcessBuilder("/bin/sh", "-c", script).redirectError(ProcessBuilder.Redirect.INHERIT)
            if (tools != null) builder.environment()["PATH"] = "$tools:" + builder.environment()["PATH"]
            val process = builder.start()
            process.outputStream.use { input -> stdin?.let { input.write(it.encodeToByteArray()) } }
            val output = process.inputStream.readAllBytes().decodeToString()
            check(process.waitFor() == 0) { "Shell failed: $script" }
            output
        }
    }

    private fun unreadableAuthor(): MemoryEndpoint {
        val author = MemoryEndpoint("primary")
        author.files[path] = bytes
        author.unreadable += path
        return author
    }

    /** A folder in memory; an unreadable file lists with the hash of no bytes and is left out of a pull, as a dataless placeholder is. */
    private class MemoryEndpoint(
        override val name: String,
    ) : RelayEndpoint {
        val files = linkedMapOf<String, ByteArray>()
        val unreadable = mutableSetOf<String>()

        override fun list(): Map<String, String> {
            return files.mapValues { (path, data) -> sha256(if (path in unreadable) ByteArray(0) else data) }
        }

        override fun pull(paths: Collection<String>): Map<String, ByteArray> {
            return paths.filter { it in files && it !in unreadable }.associateWith { files.getValue(it) }
        }

        override fun push(files: Map<String, ByteArray>) {
            this.files.putAll(files)
        }

        override fun delete(paths: Collection<String>) {
            paths.forEach { files.remove(it) }
        }
    }

    private companion object {
        fun sha256(data: ByteArray): String {
            return MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        }
    }
}
