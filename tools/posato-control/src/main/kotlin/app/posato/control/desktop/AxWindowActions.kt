package app.posato.control.desktop

class AxWindowActions(
    private val invoke: (Array<out String>) -> String
) {
    fun activate(pid: Long) {
        invoke(arrayOf("activate", pid.toString()))
    }

    /** Clicks the centre of the element at [path], for a file panel's rows, which AXPress does not select. */
    fun click(
        pid: Long,
        path: String
    ) {
        invoke(arrayOf("click", pid.toString(), path))
    }

    fun scroll(
        pid: Long,
        path: String,
        forward: Boolean
    ) {
        invoke(arrayOf("scroll", pid.toString(), path, if (forward) "down" else "up"))
    }
}
