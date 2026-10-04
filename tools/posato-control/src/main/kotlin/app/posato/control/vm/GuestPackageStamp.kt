package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/** The file in a line's host directory that names the package fingerprint the last `vm sync` copied. */
internal const val SYNCED_PACKAGE_STAMP = "synced-package.sha256"

/** A SHA-256 over every file of a staged package, its relative path and contents, so any rebuild changes it. */
internal fun packageFingerprint(app: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.walk(app).use { paths ->
        paths.filter { it.isRegularFile() }.sorted().forEach { file ->
            digest.update(file.relativeTo(app).toString().toByteArray())
            digest.update(0)
            Files.newInputStream(file).use { input -> input.copyTo(DigestSink(digest)) }
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
