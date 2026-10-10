package app.posato.linux.helper

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/** What the helper enforces, kept in a root-owned file so a restart of the service or the computer keeps it. */
internal data class HelperState(
    val applied: HelperRequest.Apply?,
    val expiredSessionId: String?,
) {
    fun encode(): String {
        val apply = applied?.let {
            "apply\t${it.sessionId}\t${it.endEpochMillis}\t${it.hosts.joinToString(",")}\t${it.executables.joinToString("\u001f")}"
        }
        return listOf(apply.orEmpty(), expiredSessionId.orEmpty()).joinToString("\n", postfix = "\n")
    }

    companion object {
        val EMPTY = HelperState(null, null)

        fun decode(text: String): HelperState {
            val lines = text.lines()
            val applied = lines.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let(HelperRequest::parse) as? HelperRequest.Apply
            return HelperState(applied, lines.getOrNull(1)?.takeIf { it.isNotEmpty() })
        }
    }
}

/** Writes [text] beside [target] and renames it over, keeping [target]'s permissions or setting [mode] for a new file. */
internal fun writeAtomically(
    target: Path,
    text: String,
    mode: String = "rw-------",
) {
    Files.createDirectories(target.parent)
    val temporary = Files.createTempFile(target.parent, ".posato-", ".tmp")
    try {
        Files.writeString(temporary, text)
        val permissions = if (Files.exists(target)) Files.getPosixFilePermissions(target) else PosixFilePermissions.fromString(mode)
        Files.setPosixFilePermissions(temporary, permissions)
        if (Files.exists(target)) {
            runCatching { Files.setOwner(temporary, Files.getOwner(target)) }
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }
}
