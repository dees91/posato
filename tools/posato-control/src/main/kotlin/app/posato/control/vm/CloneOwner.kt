package app.posato.control.vm

import app.posato.control.core.ControlJson
import app.posato.control.core.RunContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

/**
 * Who created a run clone. Clone names are machine-wide, so every worktree sees the clones of every other worktree,
 * and a refusal that names only the clone invites destroying another session's guest.
 */
@Serializable
data class CloneOwner(
    val worktree: String,
    val runId: String,
    val createdAt: String,
    val pid: Long,
    val pidStartedAt: String? = null,
)

private const val OWNER_MARKER = "posato-owner.json"

/** Tart's home directory, which Tart itself moves with `TART_HOME`. */
internal fun tartHome(): Path = System.getenv("TART_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    ?: Path.of(System.getProperty("user.home"), ".tart")

/** The marker sits in the clone's own Tart directory, so it disappears with the clone, also after a bare `tart delete`. */
private fun ownerMarker(clone: String): Path = tartHome().resolve("vms").resolve(clone).resolve(OWNER_MARKER)

/** A marker cut short, or one without the fields this driver needs, reads as no recorded owner. */
internal fun parseCloneOwner(json: String): CloneOwner? = try {
    ControlJson.lenient.decodeFromString(CloneOwner.serializer(), json)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

internal fun readCloneOwner(clone: String): CloneOwner? = try {
    ownerMarker(clone).takeIf { it.exists() }?.readText()?.let(::parseCloneOwner)
} catch (_: IOException) {
    null
}

/** Records this worktree, run, and process as the owner of a clone `tart clone` has just created. */
internal fun recordCloneOwner(
    context: RunContext,
    clone: String
) {
    val marker = ownerMarker(clone)
    if (!marker.parent.isDirectory()) {
        context.log("Tart keeps no directory for $clone at ${marker.parent}; its owner is not recorded.")
        return
    }
    val process = ProcessHandle.current()
    val owner = CloneOwner(
        worktree = context.layout.root.toString(),
        runId = context.runId,
        createdAt = Instant.now().toString(),
        pid = process.pid(),
        pidStartedAt = process.info().startInstant().map { it.toString() }.orElse(null),
    )
    Files.writeString(marker, ControlJson.pretty.encodeToString(CloneOwner.serializer(), owner))
}

/** Whether the creating process still runs: the same pid with the same start time, so a reused pid does not count. */
internal fun creatorRuns(owner: CloneOwner): Boolean = ProcessHandle.of(owner.pid)
    .map { process -> sameStart(owner.pidStartedAt, process.info().startInstant().map { it.toString() }.orElse(null)) }
    .orElse(false)

/**
 * Whether a live process's start time matches the one recorded with its pid. When neither is readable the pid alone
 * cannot tell the creator from a later process, so the creator counts as exited rather than as still booting.
 */
internal fun sameStart(
    recorded: String?,
    current: String?
): Boolean {
    return recorded != null && recorded == current
}

/** A worktree still exists while its checkout keeps its `.git` entry, a directory or a worktree's link file. */
private fun worktreeExists(owner: CloneOwner): Boolean = Path.of(owner.worktree).resolve(".git").exists()

/** One sentence naming who created [clone] and whether that worktree and process still look alive. */
internal fun describeCloneOwner(
    clone: String,
    owner: CloneOwner?,
    worktree: Path,
): String {
    if (owner == null) return "No owner is recorded for $clone: it was created outside posato-control or by an older driver."
    val place = when {
        Path.of(owner.worktree) == worktree -> "this worktree"
        worktreeExists(owner) -> "the worktree ${owner.worktree}, which still exists"
        else -> "the worktree ${owner.worktree}, which no longer exists"
    }
    val process = if (creatorRuns(owner)) "is still running, so the clone may still be booting" else "has exited"
    return "$clone was created at ${owner.createdAt} by run ${owner.runId} in $place; its creating process ${owner.pid} $process."
}
