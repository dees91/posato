package app.posato.control.apple

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

/**
 * Whether the simulator's installed bundle runs the same executable as the built one. Development builds keep one
 * version and build number, so only the executable's bytes tell a stale install from the current build.
 */
internal fun sameExecutable(
    installed: Path,
    built: Path,
): Boolean {
    val installedExecutable = installed.resolve(installed.nameWithoutExtension)
    val builtExecutable = built.resolve(built.nameWithoutExtension)
    return installedExecutable.exists() && builtExecutable.exists() && digest(installedExecutable).contentEquals(digest(builtExecutable))
}

private fun digest(file: Path): ByteArray {
    val sha = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { stream ->
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            sha.update(buffer, 0, read)
        }
    }
    return sha.digest()
}

private const val BUFFER_BYTES = 64 * 1024
