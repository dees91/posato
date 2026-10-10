package app.posato.linux.helper

/** One line from the application. Anything outside this grammar refuses the whole request. */
internal sealed interface HelperRequest {
    data class Apply(
        val sessionId: String,
        val endEpochMillis: Long,
        val hosts: List<String>,
        val executables: List<String>,
    ) : HelperRequest

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

        fun parse(line: String): HelperRequest? {
            val fields = line.split('\t')
            return when (fields.first()) {
                "apply" -> fields.takeIf { it.size == APPLY_FIELDS }?.let(::apply)
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
            return if (valid) Apply(sessionId, end, hosts, executables) else null
        }

        private fun isHost(host: String): Boolean {
            return host.length <= MAX_HOST_LENGTH && host.split('.').let { labels -> labels.size >= 2 && labels.all(label::matches) }
        }

        /** An executable's absolute path, or one snap's directory, which holds every revision of that snap. */
        private fun isPath(path: String): Boolean {
            val wellFormed = path.startsWith("/") && path.length <= MAX_PATH_LENGTH && path.none { it.isISOControl() } && "/../" !in "$path/"
            return wellFormed && (!path.endsWith("/") || snapDirectory.matches(path))
        }

        private val snapDirectory = Regex("/snap/[a-z0-9][a-z0-9-]*(_[a-z0-9]+)?/")
    }
}
