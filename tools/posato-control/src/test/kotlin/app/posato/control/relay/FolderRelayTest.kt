package app.posato.control.relay

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        val author = MemoryEndpoint("primary").apply { files[path] = bytes; unreadable += path }
        val other = MemoryEndpoint("linux")
        val relay = FolderRelay(Files.createTempDirectory("relay-hub"), listOf(author, other))

        relay.round()
        relay.round()

        assertTrue(path in author.files)
        assertFalse(path in other.files)
    }

    @Test
    fun `given a file that becomes readable when the next round runs then every device receives it`() {
        val author = MemoryEndpoint("primary").apply { files[path] = bytes; unreadable += path }
        val other = MemoryEndpoint("linux")
        val relay = FolderRelay(Files.createTempDirectory("relay-hub"), listOf(author, other))
        relay.round()

        author.unreadable.clear()
        relay.round()

        assertContentEquals(bytes, other.files[path])
        assertContentEquals(bytes, author.files[path])
    }

    /** A folder in memory; an unreadable file lists with the hash of no bytes and is left out of a pull, as a dataless placeholder is. */
    private class MemoryEndpoint(override val name: String) : RelayEndpoint {
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
