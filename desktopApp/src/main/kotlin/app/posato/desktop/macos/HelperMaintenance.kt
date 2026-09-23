package app.posato.desktop.macos

import java.util.concurrent.TimeUnit

internal class HelperMaintenance(
    private val exitTimeoutMillis: Long = HELPER_EXIT_TIMEOUT_MILLIS,
) {
    private val monitor = Any()
    private val spawned = mutableListOf<Process>()

    @Volatile private var backstopEngaged = false

    @Volatile private var spawnsRefused = false

    val isBackstopEngaged: Boolean
        get() {
            return backstopEngaged
        }

    val allowsSpawns: Boolean
        get() {
            return !spawnsRefused
        }

    fun engageBackstop() {
        backstopEngaged = true
    }

    fun releaseBackstop() {
        backstopEngaged = false
    }

    fun spawn(start: () -> Process): Process {
        return synchronized(monitor) {
            check(!spawnsRefused)
            spawned.removeAll { process -> !process.isAlive }
            start().also { process -> spawned += process }
        }
    }

    fun refuseSpawnsAndStopHelpers(): Boolean {
        val running = synchronized(monitor) {
            spawnsRefused = true
            spawned.toList()
        }
        return running.all { process -> stop(process) }
    }

    fun allowSpawns() {
        synchronized(monitor) {
            spawnsRefused = false
        }
    }

    private fun stop(process: Process): Boolean {
        if (!process.isAlive) {
            return true
        }
        process.destroy()
        if (process.waitFor(exitTimeoutMillis, TimeUnit.MILLISECONDS)) {
            return true
        }
        process.destroyForcibly()
        return process.waitFor(exitTimeoutMillis, TimeUnit.MILLISECONDS)
    }
}

internal val processHelperMaintenance: HelperMaintenance = HelperMaintenance()

private const val HELPER_EXIT_TIMEOUT_MILLIS: Long = 5_000L
