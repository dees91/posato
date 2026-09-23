package app.posato.desktop.update

import java.io.Closeable
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class FileInstanceLock(
    path: Path,
) : AdmissionInstanceLock,
    Closeable {
    private val channel: FileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
    private var instanceLock: FileLock? = null
    private var admissionLock: FileLock? = null

    @Synchronized
    fun acquireShared(): Boolean {
        if (instanceLock != null) {
            return true
        }
        instanceLock = tryLock(INSTANCE_REGION, shared = true)
        return instanceLock != null
    }

    @Synchronized
    override fun tryUpgradeForAdmission(): Boolean {
        val held = instanceLock ?: return false
        if (!held.isShared) {
            return true
        }
        val admission = tryLock(ADMISSION_REGION, shared = false) ?: return false
        held.release()
        val exclusive = tryLock(INSTANCE_REGION, shared = false)
        if (exclusive == null) {
            instanceLock = tryLock(INSTANCE_REGION, shared = true)
            admission.release()
            return false
        }
        instanceLock = exclusive
        admissionLock = admission
        return true
    }

    @Synchronized
    override fun downgradeAfterMaintenance() {
        val held = instanceLock ?: return
        if (held.isShared) {
            return
        }
        held.release()
        instanceLock = tryLock(INSTANCE_REGION, shared = true)
        admissionLock?.release()
        admissionLock = null
    }

    @Synchronized
    override fun close() {
        channel.close()
        instanceLock = null
        admissionLock = null
    }

    private fun tryLock(
        region: Long,
        shared: Boolean,
    ): FileLock? {
        return try {
            channel.tryLock(region, 1L, shared)
        } catch (_: IOException) {
            null
        } catch (_: OverlappingFileLockException) {
            null
        }
    }
}

private const val INSTANCE_REGION: Long = 0L
private const val ADMISSION_REGION: Long = 1L
