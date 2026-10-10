package app.posato.linux.helper

/** The marked block of `/etc/hosts` that a pause owns; every line it writes carries the [TAG] so any copy can be removed. */
internal object HostsBlock {
    private const val BEGIN = "# BEGIN POSATO"
    private const val END = "# END POSATO"
    private const val TAG = " # posato"
    private const val WWW = "www."

    /** Removes every Posato line, then appends one block for [hosts]; the person's own lines never change. */
    fun rewrite(
        existing: String,
        hosts: List<String>,
    ): String {
        val kept = existing.lines().dropLastWhile { it.isEmpty() }.filterNot { line -> line == BEGIN || line == END || line.endsWith(TAG) }
        val names = hosts.flatMap { host -> if (host.startsWith(WWW)) listOf(host) else listOf(host, WWW + host) }.distinct()
        val block = if (names.isEmpty()) {
            emptyList()
        } else {
            listOf(BEGIN) + names.flatMap { name -> listOf("0.0.0.0 $name$TAG", ":: $name$TAG") } + END
        }
        val lines = kept + block
        return if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
    }
}
