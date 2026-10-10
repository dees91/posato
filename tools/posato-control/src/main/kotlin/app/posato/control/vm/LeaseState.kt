package app.posato.control.vm

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Whether a line's clone is free, used by a session, or left behind by a worktree that no longer exists. */
enum class LeaseState { FREE, HELD, STALE }

/**
 * What another session will release and so is worth waiting for: a running golden VM, the line's clone while it runs
 * or its creating process lives, or a second running guest. Null when nothing blocks, or when only something that
 * never frees itself does: a missing golden VM, or a stopped clone whose creator has exited.
 */
internal fun waitableBlock(
    vms: List<TartVm>,
    golden: String,
    clone: String,
    creatorAlive: (String) -> Boolean,
): String? {
    val source = vms.firstOrNull { it.name == golden } ?: return null
    val existing = vms.firstOrNull { it.name == clone }
    return when {
        source.running -> "The golden VM '$golden' is running."
        existing != null -> "The clone '$clone' is in use.".takeIf { existing.running || creatorAlive(clone) }
        vms.count { it.running } >= MAX_RUNNING_GUESTS -> "Two macOS guests already run."
        else -> null
    }
}

/**
 * A clone is held while it runs or while the worktree that created it still exists; a stopped clone whose worktree
 * is gone, or that has no recorded owner, is stale. A stale clone is only reported: deleting it is the job of
 * `vm destroy`, which refuses a guest still linked to an iCloud workspace.
 */
internal fun leaseState(
    exists: Boolean,
    running: Boolean,
    owner: CloneOwner?,
    worktreeExists: Boolean,
): LeaseState = when {
    !exists -> LeaseState.FREE
    running -> LeaseState.HELD
    owner != null && worktreeExists -> LeaseState.HELD
    else -> LeaseState.STALE
}

/** Serializes creators inside this process; the file lock below serializes processes. */
private val CREATE_MONITOR = Any()

/**
 * Runs [block] while holding the machine-wide clone lock, so a check of Tart's VMs and the clone it allows happen as
 * one step: two sessions that created one line at once both saw no clone and both cloned (review of #166). A file
 * lock alone does not serialize threads of one process, which the JVM refuses to lock twice, so a monitor does that.
 */
internal fun <T> withCreateLock(
    lockFile: Path,
    block: () -> T,
): T = synchronized(CREATE_MONITOR) {
    Files.createDirectories(lockFile.parent)
    FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        channel.lock().use { block() }
    }
}
