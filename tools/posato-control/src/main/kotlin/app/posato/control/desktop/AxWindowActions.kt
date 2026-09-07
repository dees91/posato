package app.posato.control.desktop

class AxWindowActions(
    private val invoke: (Array<out String>) -> String
) {
    fun activate(pid: Long) {
        invoke(arrayOf("activate", pid.toString()))
    }

    fun scroll(
        pid: Long,
        path: String,
        forward: Boolean
    ) {
        invoke(arrayOf("scroll", pid.toString(), path, if (forward) "down" else "up"))
    }
}
