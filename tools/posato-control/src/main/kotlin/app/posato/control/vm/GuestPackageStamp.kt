package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RepoLayout
import app.posato.control.core.RunContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * The file in the guest that names the fingerprint of the package the last `vm sync` copied there. It lives in the
 * guest because every worktree shares a line's clone.
 */
internal const val GUEST_PACKAGE_STAMP = "build/verification/package.sha256"

/**
 * The file in the guest that names the fingerprint of the driver the last `vm sync` copied there: the guest's own
 * posato-control runs every `--vm` command, so a host rebuild of the driver reaches it only through `vm sync`.
 */
internal const val GUEST_TOOLING_STAMP = "build/verification/tooling.sha256"

/** A SHA-256 over every file of a staged package, its relative path and contents, so any rebuild changes it. */
internal fun packageFingerprint(app: Path): String = treeFingerprint(app, listOf(app))

/**
 * A SHA-256 over every file under the driver [paths], relative to the repository [root], so a rebuilt distribution,
 * a changed fixture, or a rebuilt bridge changes it. A missing path adds nothing.
 */
internal fun toolingFingerprint(
    root: Path,
    paths: List<String>,
): String = treeFingerprint(root, paths.map(root::resolve))

private fun treeFingerprint(
    base: Path,
    trees: List<Path>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    trees.filter { Files.exists(it) }.forEach { tree ->
        Files.walk(tree).use { paths ->
            paths.filter { it.isRegularFile() }.sorted().forEach { file ->
                digest.update(file.relativeTo(base).toString().toByteArray())
                digest.update(0)
                Files.newInputStream(file).use { input -> input.copyTo(DigestSink(digest)) }
            }
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Stops a development launch in a guest that holds another package than the one staged on the host: a rebuild
 * reaches the guest only through `vm sync`. A guest synced before stamps existed counts as outdated.
 */
internal fun refuseOutdatedPackage(
    synced: String?,
    staged: String?,
    line: VmLine,
) {
    if (staged == null || synced == staged) return
    throw ControlException(
        ErrorCode.PACKAGE_OUTDATED,
        "${line.cloneName} holds another package than the one staged on the host.",
        "Run `posato-control vm sync --line ${line.id}` to copy the staged package, then launch again.",
    )
}

/**
 * Stops a `--vm` command when the guest holds another driver than the host's: the guest's copy runs the command, so a
 * fix rebuilt on the host would silently not apply. A guest synced before tooling stamps existed counts as outdated.
 */
internal fun refuseOutdatedTooling(
    synced: String?,
    host: String,
    line: VmLine,
) {
    if (synced == host) return
    throw ControlException(
        ErrorCode.TOOL_OUTDATED,
        "${line.cloneName} holds another posato-control than this worktree's build.",
        "Run `posato-control vm sync --line ${line.id}` to copy the driver, then run the command again.",
    )
}

/** The files `vm sync` copies as the guest's driver, relative to the repository root. */
internal fun driverPaths(layout: RepoLayout): List<String> = listOf(
    "settings.gradle.kts",
    "tools/posato-control/build/install",
    "tools/posato-control/native",
    "tools/posato-control/fixtures",
    layout.relativize(layout.accessibilityBridgeBinary),
    layout.relativize(layout.accessibilityBridgeCommand),
)

/** Stops a command that the guest's driver would run when that driver is not this worktree's current build. */
internal fun requireCurrentTooling(
    context: RunContext,
    line: VmLine,
) {
    val guest = Tart(context).exec(line.cloneName, "cat \"$GUEST_ROOT/$GUEST_TOOLING_STAMP\" 2>/dev/null")
        .requireSuccess(ErrorCode.VM_UNAVAILABLE, "Reading the synced driver in ${line.cloneName}")
        .stdout.trim()
    refuseOutdatedTooling(guest.ifEmpty { null }, toolingFingerprint(context.layout.root, driverPaths(context.layout)), line)
}

private class DigestSink(
    private val digest: MessageDigest
) : java.io.OutputStream() {
    override fun write(b: Int) = digest.update(b.toByte())

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int
    ) = digest.update(b, off, len)
}
