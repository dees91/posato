package app.posato.desktop.linux.status

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.Variant

/**
 * Posato's StatusNotifierItem: identity, icon, tooltip, and menu, read by the panel as properties. Each property is a
 * [Variant] because dbus-java answers org.freedesktop.DBus.Properties.Get with the getter's own type, `(a(iiay))`
 * instead of `(v)` for the pixmaps, which GNOME's AppIndicator extension rejects and then shows an empty menu.
 */
internal class StatusItem(
    private val icons: StatusIcons,
    private val state: () -> StatusAreaState,
    private val onShow: (String) -> Unit,
) : StatusNotifierItem {
    override val category: Variant<*> get() = Variant("ApplicationStatus")

    override val id: Variant<*> get() = Variant("posato")

    override val title: Variant<*> get() = Variant(state().title)

    override val status: Variant<*> get() = Variant("Active")

    override val windowId: Variant<*> get() = Variant(0)

    override val iconThemePath: Variant<*> get() = Variant(icons.themePath)

    override val iconName: Variant<*> get() = Variant(if (state().filled) icons.filledName else icons.outlineName)

    override val iconPixmap: Variant<*> get() = Variant(if (state().filled) icons.filledPixmaps else icons.outlinePixmaps, PIXMAPS)

    override val overlayIconName: Variant<*> get() = Variant("")

    override val attentionIconName: Variant<*> get() = Variant("")

    override val toolTip: Variant<*> get() = state().let { Variant(ToolTip("", emptyList(), it.title, it.description), TOOLTIP) }

    override val itemIsMenu: Variant<*> get() = Variant(false)

    override val menu: Variant<*> get() = Variant(DBusPath(LinuxStatusArea.MENU_PATH))

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

    private companion object {
        const val PIXMAPS = "a(iiay)"
        const val TOOLTIP = "(sa(iiay)ss)"
    }
}
