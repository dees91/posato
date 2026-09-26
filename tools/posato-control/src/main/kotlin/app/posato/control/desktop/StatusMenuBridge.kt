package app.posato.control.desktop

internal fun AxBridge.statusMenu(
    pid: Long,
    mode: String,
    title: String?
): StatusMenuResult = decode(StatusMenuResult.serializer(), invoke("status-menu", pid.toString(), mode, title.orEmpty()))

internal fun AxBridge.closeWindow(pid: Long) {
    invoke("close-window", pid.toString())
}
