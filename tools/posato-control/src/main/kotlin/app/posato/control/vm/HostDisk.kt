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

/** What may be deleted to free space because a later run regenerates it. */
internal const val DISK_SPACE_HINT = "Free space by deleting what regenerates: old runs under build/verification/runs, the build " +
    "directories of worktrees you no longer use (`./gradlew clean`), Xcode's DerivedData, ~/.gradle/caches, and Tart's image " +
    "caches (`tart prune`). Never delete a golden VM, or a clone that another worktree created."

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

/** Refuses to clone a 60 GB guest disk onto a volume that a run could fill. */
internal fun refuseLowDisk(free: FreeSpace) {
    if (free.bytes >= MIN_FREE_DISK_BYTES) return
    throw ControlException(
        ErrorCode.DISK_SPACE_LOW,
        "Only ${gigabytes(free.bytes)} is free on the volume holding ${free.path}; a run with a clone needs at least " +
            "${gigabytes(MIN_FREE_DISK_BYTES)}.",
        DISK_SPACE_HINT,
    )
}

private const val BYTES_PER_GB = 1_000_000_000.0
