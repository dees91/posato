package app.posato.windows.helper

/** What the service enforces, kept in the protected folder so a restart of the service or the computer keeps it. */
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
