package app.posato.desktop.resident

import java.io.Closeable
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * One Posato per user on a host that draws its own window (LINUX-003, PLATFORM-002): an exclusive lock on
 * `instance.lock` next to the database, taken before the database opens, held until the process ends. The operating
 * system drops it with the process, so a crash leaves no stale lock.
 */
internal class InstanceLock(
    private val path: Path,
) : Closeable {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun tryAcquire(): Boolean {
        if (lock != null) return true
        val opened = runCatching {
            Files.createDirectories(path.parent)
            FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
        }.getOrNull() ?: return false
        val acquired = try {
            opened.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        } catch (_: IOException) {
            null
        }
        if (acquired == null) {
            opened.close()
            return false
        }
        channel = opened
        lock = acquired
        return true
    }

    @Synchronized
    override fun close() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }
}

internal enum class ResidentStartup { RUN, FORWARDED, EXIT }

/**
 * Whether this launch runs Posato: with the lock it does; without it, it asks the running Posato to show its window
 * and exits either way, never opening the database a second time.
 */
internal fun residentStartup(
    lock: InstanceLock,
    forwardShow: () -> Boolean,
): ResidentStartup {
    if (lock.tryAcquire()) return ResidentStartup.RUN
    return if (forwardShow()) ResidentStartup.FORWARDED else ResidentStartup.EXIT
}
