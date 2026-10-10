package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.exists

/**
 * The free space a verification run needs on the host. The unattended verification guide asks for about 80 GB before
 * the two golden VMs exist, and each takes about 27 GB, which leaves about 25 GB of working room. A run spends it
 * on clones, whose copy-on-write disks grow by about 2.5 GB at boot alone, a desktop build of about 2 GB, and
 * `./gradlew quality`; a session with 6.5 GB free failed midway through the quality gate with "No space left on
 * device" (release 1.3 retro). Below 20 GB a run with two clones and a full quality pass can run out midway.
 */
internal const val MIN_FREE_DISK_BYTES = 20_000_000_000L

/**
 * The minimum in force: [MIN_FREE_DISK_BYTES], or `POSATO_CONTROL_MIN_FREE_DISK_GB` when it is above it, which only
 * verifies the guards without filling a disk. A lower value is ignored with a warning: the variable never weakens them.
 */
internal fun minFreeDiskBytes(): Long {
    val raw = System.getenv(MIN_FREE_DISK_ENV) ?: return MIN_FREE_DISK_BYTES
    val requested = raw.toLongOrNull()?.let { it * BYTES_PER_GB.toLong() }
    if (requested != null && requested >= MIN_FREE_DISK_BYTES) return requested
    if (!thresholdWarned) {
        thresholdWarned = true
        System.err.println("posato-control: ignoring $MIN_FREE_DISK_ENV=$raw; it may only raise the ${gigabytes(MIN_FREE_DISK_BYTES)} minimum.")
    }
    return MIN_FREE_DISK_BYTES
}

/** Below this no guest command runs, also in a clone created with `--allow-low-disk`. */
internal const val HARD_FLOOR_DISK_BYTES = 5_000_000_000L

private const val MIN_FREE_DISK_ENV = "POSATO_CONTROL_MIN_FREE_DISK_GB"

/** One warning per process about an ignored threshold override. */
@Volatile private var thresholdWarned = false

/** What may be deleted to free space because a later run regenerates it. */
internal const val DISK_SPACE_HINT = "Free space by deleting what regenerates: old runs under build/verification/runs, the build " +
    "directories of worktrees you no longer use (`./gradlew clean`), Xcode's DerivedData, ~/.gradle/caches, and Tart's image " +
    "caches (`tart prune`). Never delete a golden VM, or a clone that another worktree created. The figure leaves out " +
    "purgeable space, such as Time Machine local snapshots (`tmutil listlocalsnapshots /`) and caches, which macOS frees " +
    "only when a write needs it."

/** The free space of the volume holding [path], or of its nearest existing parent. */
internal data class FreeSpace(
    val path: Path,
    val bytes: Long,
)

internal fun gigabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f GB", bytes / BYTES_PER_GB)

/** The volume with the least free space among those holding the repository and Tart's VMs, usually one and the same. */
internal fun lowestFreeSpace(repository: Path): FreeSpace = listOf(repository, tartHome()).map { path ->
    val existing = generateSequence(path.toAbsolutePath()) { it.parent }.first { it.exists() }
    FreeSpace(path, Files.getFileStore(existing).usableSpace)
}.minBy { it.bytes }

/**
 * Refuses to clone a 60 GB guest disk onto a volume that a run could fill. With [allowLowDisk], for a run known to fit
 * or a volume whose purgeable space the figure leaves out, it returns the shortage as a warning instead; null when
 * the space suffices.
 */
internal fun lowDiskWarning(
    free: FreeSpace,
    allowLowDisk: Boolean
): String? {
    if (free.bytes >= minFreeDiskBytes()) return null
    if (allowLowDisk && free.bytes < HARD_FLOOR_DISK_BYTES) {
        throw ControlException(
            ErrorCode.DISK_SPACE_LOW,
            "Only ${gigabytes(free.bytes)} is free on the volume holding ${free.path}; no clone is created below " +
                "${gigabytes(HARD_FLOOR_DISK_BYTES)}, not even with --allow-low-disk.",
            DISK_SPACE_HINT,
        )
    }
    val shortage = "Only ${gigabytes(free.bytes)} is free on the volume holding ${free.path}; a run with a clone needs at " +
        "least ${gigabytes(minFreeDiskBytes())}."
    if (allowLowDisk) return shortage
    throw ControlException(
        ErrorCode.DISK_SPACE_LOW,
        shortage,
        "$DISK_SPACE_HINT Pass --allow-low-disk to create the clone anyway.",
    )
}

/**
 * Stops a command that works inside [line]'s running guest while the host is below the minimum. A guest's
 * copy-on-write disk grows with what it writes, so a load test inside one clone once filled the host to 100%; then
 * every tool call of every session failed with "No space left on device" until a person freed space (release 1.4
 * retro). A clone created with `--allow-low-disk` runs down to [HARD_FLOOR_DISK_BYTES]. Commands that free space or
 * only read state (`vm destroy`, `vm shutdown`, `vm leases`, `doctor`, `flow icloud remove`) stay available.
 */
internal fun requireGuestRoom(
    repository: Path,
    line: VmLine,
) {
    val free = lowestFreeSpace(repository)
    val minimum = if (lowDiskAllowed(line.cloneName)) HARD_FLOOR_DISK_BYTES else minFreeDiskBytes()
    if (free.bytes >= minimum) return
    throw ControlException(
        ErrorCode.DISK_SPACE_LOW,
        "Only ${gigabytes(free.bytes)} is free on the volume holding ${free.path}; guest commands in ${line.cloneName} stop " +
            "below ${gigabytes(minimum)} so a growing clone cannot fill the host.",
        "Destroy the clones you own (`posato-control vm destroy --line <line>`); a guest still linked to iCloud first " +
            "runs `flow icloud remove`, which this guard allows, or is destroyed with `--keep-workspace`. Then: $DISK_SPACE_HINT",
    )
}

private const val BYTES_PER_GB = 1_000_000_000.0
