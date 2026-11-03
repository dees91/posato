package app.posato.windows.helper

/**
 * One line from the application, in the Linux helper's grammar. Anything outside it refuses the whole request; an
 * executable is an absolute local Windows path to an `.exe`.
 */
internal sealed interface HelperRequest {
    data class Apply(
        val sessionId: String,
        val endEpochMillis: Long,
        val hosts: List<String>,
        val executables: List<String>,
        val windows: List<HostWindowPlan> = emptyList(),
    ) : HelperRequest {
        override fun toString(): String {
            return "Apply(redacted)"
        }
    }

    data object Clear : HelperRequest

    data object Status : HelperRequest

    data class Acknowledge(
        val sessionId: String,
    ) : HelperRequest

    companion object {
        private const val MAX_HOSTS = 4_096
        private const val MAX_HOST_LENGTH = 253
        private const val MAX_EXECUTABLES = 64
        private const val MAX_PATH_LENGTH = 4_096
        private const val APPLY_FIELDS = 5
        private val label = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
        private val sessionPattern = Regex("[A-Za-z0-9_-]{1,128}")
        private val drive = Regex("[A-Za-z]:\\\\.*")
        private const val RESERVED_CHARACTERS = "<>:\"/\\|?*"

        fun parse(line: String): HelperRequest? {
            val fields = line.split('\t')
            return when (fields.first()) {
                "apply" -> fields.takeIf { it.size == APPLY_FIELDS || it.size == APPLY_FIELDS + 1 }?.let(::apply)
                "clear" -> Clear.takeIf { fields.size == 1 }
                "status" -> Status.takeIf { fields.size == 1 }
                "ack" -> fields.getOrNull(1)?.takeIf { fields.size == 2 && sessionPattern.matches(it) }?.let(::Acknowledge)
                else -> null
            }
        }

        private fun apply(fields: List<String>): Apply? {
            val sessionId = fields[1].takeIf(sessionPattern::matches) ?: return null
            val end = fields[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val hosts = fields[3].split(',').filter { it.isNotEmpty() }
            val executables = fields[4].split('\u001f').filter { it.isNotEmpty() }
            val valid = hosts.size <= MAX_HOSTS && hosts.all(::isHost) && executables.size <= MAX_EXECUTABLES && executables.all(::isPath)
            val windows = WindowPlans.parse(fields.getOrElse(APPLY_FIELDS) { "" }) ?: return null
            return if (valid) Apply(sessionId, end, hosts, executables, windows) else null
        }

        private fun isHost(host: String): Boolean {
            return host.length <= MAX_HOST_LENGTH && host.split('.').let { labels -> labels.size >= 2 && labels.all(label::matches) }
        }

        /**
         * `X:\...\name.exe` with plain names between single separators: no device, UNC, or `\\?\` form, no `.` or `..`,
         * no stream, and nothing the file system would read as a wildcard.
         */
        private fun isPath(path: String): Boolean {
            if (path.length > MAX_PATH_LENGTH || !drive.matches(path) || path.any { it.isISOControl() }) return false
            val names = path.substring(DRIVE_PREFIX).split('\\')
            val plain = names.all { name -> name.isNotEmpty() && name != "." && name != ".." && name.none { it in RESERVED_CHARACTERS } }
            return plain && names.last().endsWith(".exe", ignoreCase = true)
        }

        private const val DRIVE_PREFIX = 3
    }
}

/** The line this request travels as, and is kept as in the state file; plans add the sixth field. */
internal fun HelperRequest.Apply.line(): String {
    val five = "apply\t$sessionId\t$endEpochMillis\t${hosts.joinToString(",")}\t${executables.joinToString("\u001f")}"
    return if (windows.isEmpty()) five else "$five\t${WindowPlans.encode(windows)}"
}
