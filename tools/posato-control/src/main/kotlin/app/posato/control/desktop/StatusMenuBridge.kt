package app.posato.control.desktop

internal fun AxBridge.statusMenu(
    pid: Long,
    mode: String,
    title: String?
): StatusMenuResult = decode(StatusMenuResult.serializer(), invoke("status-menu", pid.toString(), mode, title.orEmpty()))

internal fun AxBridge.closeWindow(pid: Long) {
    invoke("close-window", pid.toString())
}

internal fun AxBridge.swipeBack(
    pid: Long,
    complete: Boolean
) {
    invoke("swipe-back", pid.toString(), if (complete) "1" else "0")
}

/** Sets the main window's size and returns the size it took, as JSON with `width` and `height`. */
internal fun AxBridge.resizeWindow(
    pid: Long,
    width: Int,
    height: Int
): String = invoke("resize-window", pid.toString(), width.toString(), height.toString())
