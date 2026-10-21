package app.posato.desktop.linux.status

import org.freedesktop.dbus.DBusPath

/** Posato's StatusNotifierItem: identity, icon, tooltip, and menu, read by the panel as properties. */
internal class StatusItem(
    private val icons: StatusIcons,
    private val state: () -> StatusAreaState,
    private val onShow: (String) -> Unit,
) : StatusNotifierItem {
    override val category: String get() = "ApplicationStatus"

    override val id: String get() = "posato"

    override val title: String get() = state().title

    override val status: String get() = "Active"

    override val windowId: Int get() = 0

    override val iconThemePath: String get() = icons.themePath

    override val iconName: String get() = if (state().filled) icons.filledName else icons.outlineName

    override val iconPixmap: List<IconPixmap> get() = if (state().filled) icons.filledPixmaps else icons.outlinePixmaps

    override val overlayIconName: String get() = ""

    override val attentionIconName: String get() = ""

    override val toolTip: ToolTip get() = state().let { ToolTip("", emptyList(), it.title, it.description) }

    override val itemIsMenu: Boolean get() = false

    override val menu: DBusPath get() = DBusPath(LinuxStatusArea.MENU_PATH)

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
