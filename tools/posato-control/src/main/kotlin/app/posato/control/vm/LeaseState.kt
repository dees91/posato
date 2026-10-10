package app.posato.control.vm

import app.posato.control.core.RunContext
import java.time.Duration

/** Whether a line's clone is free, used by a session, or left behind by a worktree that no longer exists. */
enum class LeaseState { FREE, HELD, STALE }

/**
 * What another session will release and so is worth waiting for: the line's clone, a running golden VM, or two
 * running guests. Null when nothing blocks, or when only a missing golden VM does, which no wait repairs.
 */
internal fun waitableBlock(
    vms: List<TartVm>,
    golden: String,
    clone: String,
): String? {
    val source = vms.firstOrNull { it.name == golden } ?: return null
    return when {
        source.running -> "The golden VM '$golden' is running."
        vms.any { it.name == clone } -> "The clone '$clone' exists."
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

/**
 * Waits up to [minutes] for what another session will release: the line's clone, a running golden VM, or a second
 * running guest. Parallel sessions share both, and before this wait each one invented its own lock (release 1.4
 * retro). Returns the milliseconds waited; the clone refusal afterwards names whatever still blocks.
 */
internal fun awaitLine(
    tart: Tart,
    context: RunContext,
    golden: String,
    clone: String,
    minutes: Long,
): Long {
    val started = System.currentTimeMillis()
    val deadline = started + Duration.ofMinutes(minutes).toMillis()
    while (System.currentTimeMillis() < deadline) {
        val block = waitableBlock(tart.list(), golden, clone) ?: break
        context.log("Waiting for $clone: $block")
        Thread.sleep(LINE_POLL_MS)
    }
    return System.currentTimeMillis() - started
}

private const val LINE_POLL_MS = 30_000L
