package app.posato.desktop.linux.status

import org.freedesktop.dbus.DBusPath

/** Posato's StatusNotifierItem: identity, icon, tooltip, and menu, read by the panel as properties. */
internal class StatusItem(
    private val icons: StatusIcons,
    private val state: () -> StatusAreaState,
    private val onShow: (String) -> Unit,
) : StatusNotifierItem {
    override fun getCategory(): String = "ApplicationStatus"

    override fun getId(): String = "posato"

    override fun getTitle(): String = state().title

    override fun getStatus(): String = "Active"

    override fun getWindowId(): Int = 0

    override fun getIconThemePath(): String = icons.themePath

    override fun getIconName(): String = if (state().filled) icons.filledName else icons.outlineName

    override fun getIconPixmap(): List<IconPixmap> = if (state().filled) icons.filledPixmaps else icons.outlinePixmaps

    override fun getOverlayIconName(): String = ""

    override fun getAttentionIconName(): String = ""

    override fun getToolTip(): ToolTip = state().let { ToolTip("", emptyList(), it.title, it.description) }

    override fun getItemIsMenu(): Boolean = false

    override fun getMenu(): DBusPath = DBusPath(LinuxStatusArea.MENU_PATH)

    override fun activate(
        x: Int,
        y: Int,
    ) = onShow("")

    override fun secondaryActivate(
        x: Int,
        y: Int,
    ) = onShow("")

    override fun contextMenu(
        x: Int,
        y: Int,
    ) = Unit

    override fun scroll(
        delta: Int,
        orientation: String,
    ) = Unit

    override fun getObjectPath(): String = LinuxStatusArea.ITEM_PATH
}
