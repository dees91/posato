package app.posato.control.apple

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

/**
 * Whether the simulator's installed bundle runs the same code as the built one. Development builds keep one version
 * and build number, so only the code's bytes tell a stale install from the current build. A Debug build keeps its code
 * in `<name>.debug.dylib` behind a launcher stub that rarely changes between builds, so both files are compared.
 */
internal fun sameExecutable(
    installed: Path,
    built: Path,
): Boolean {
    val files = codeFiles(built)
    return files.isNotEmpty() && codeFiles(installed) == files && files.all { name -> sameDigest(installed.resolve(name), built.resolve(name)) }
}

private fun codeFiles(bundle: Path): List<String> =
    listOf(bundle.nameWithoutExtension, "${bundle.nameWithoutExtension}$DEBUG_DYLIB_SUFFIX").filter { bundle.resolve(it).exists() }

private fun sameDigest(
    installed: Path,
    built: Path,
): Boolean = digest(installed).contentEquals(digest(built))

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
private const val DEBUG_DYLIB_SUFFIX = ".debug.dylib"
