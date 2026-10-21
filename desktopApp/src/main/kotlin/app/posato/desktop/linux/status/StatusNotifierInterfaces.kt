package app.posato.desktop.linux.status

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/*
 * The D-Bus surface of Posato's status icon on Linux (LINUX-003): the freedesktop StatusNotifierItem that Ubuntu's
 * AppIndicator extension, KDE Plasma, and the Xfce and MATE panels show, its com.canonical.dbusmenu menu, the watcher
 * it registers with, and Posato's own name for a second launch. Member names follow the specifications through
 * @DBusMemberName.
 */

/** The item a panel draws; dbus-java serves the bound properties through org.freedesktop.DBus.Properties. */
@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
    @DBusBoundProperty(name = "Category")
    fun getCategory(): String

    @DBusBoundProperty(name = "Id")
    fun getId(): String

    @DBusBoundProperty(name = "Title")
    fun getTitle(): String

    @DBusBoundProperty(name = "Status")
    fun getStatus(): String

    @DBusBoundProperty(name = "WindowId")
    fun getWindowId(): Int

    @DBusBoundProperty(name = "IconThemePath")
    fun getIconThemePath(): String

    @DBusBoundProperty(name = "IconName")
    fun getIconName(): String

    @DBusBoundProperty(name = "IconPixmap")
    fun getIconPixmap(): List<IconPixmap>

    @DBusBoundProperty(name = "OverlayIconName")
    fun getOverlayIconName(): String

    @DBusBoundProperty(name = "AttentionIconName")
    fun getAttentionIconName(): String

    @DBusBoundProperty(name = "ToolTip")
    fun getToolTip(): ToolTip

    @DBusBoundProperty(name = "ItemIsMenu")
    fun getItemIsMenu(): Boolean

    @DBusBoundProperty(name = "Menu")
    fun getMenu(): DBusPath

    @DBusMemberName("Activate")
    fun activate(
        x: Int,
        y: Int,
    )

    @DBusMemberName("SecondaryActivate")
    fun secondaryActivate(
        x: Int,
        y: Int,
    )

    @DBusMemberName("ContextMenu")
    fun contextMenu(
        x: Int,
        y: Int,
    )

    @DBusMemberName("Scroll")
    fun scroll(
        delta: Int,
        orientation: String,
    )

    class NewIcon(
        path: String,
    ) : DBusSignal(path)

    class NewTitle(
        path: String,
    ) : DBusSignal(path)

    class NewToolTip(
        path: String,
    ) : DBusSignal(path)

    class NewStatus(
        path: String,
        status: String,
    ) : DBusSignal(path, status)
}

/** The panel side: a running watcher learns about Posato's item through this call. */
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    @DBusMemberName("RegisterStatusNotifierItem")
    fun registerStatusNotifierItem(service: String)
}

/** The menu the panel draws for the item; only the calls a panel makes are answered. */
@DBusInterfaceName("com.canonical.dbusmenu")
interface DbusMenu : DBusInterface {
    @DBusBoundProperty(name = "Version")
    fun getVersion(): UInt32

    @DBusBoundProperty(name = "TextDirection")
    fun getTextDirection(): String

    @DBusBoundProperty(name = "Status")
    fun getStatus(): String

    @DBusBoundProperty(name = "IconThemePath")
    fun getIconThemePath(): List<String>

    @DBusMemberName("GetLayout")
    fun getLayout(
        parentId: Int,
        recursionDepth: Int,
        propertyNames: List<String>,
    ): MenuLayoutReply<UInt32, MenuLayout>

    @DBusMemberName("GetGroupProperties")
    fun getGroupProperties(
        ids: List<Int>,
        propertyNames: List<String>,
    ): List<MenuItemProperties>

    @DBusMemberName("GetProperty")
    fun getProperty(
        id: Int,
        name: String,
    ): Variant<*>

    @DBusMemberName("Event")
    fun event(
        id: Int,
        eventId: String,
        data: Variant<*>,
        timestamp: UInt32,
    )

    @DBusMemberName("AboutToShow")
    fun aboutToShow(id: Int): Boolean

    class LayoutUpdated(
        path: String,
        revision: UInt32,
        parent: Int,
    ) : DBusSignal(path, revision, parent)
}

/** Posato's own name on the session bus: a second launch asks the running one to show its window. */
@DBusInterfaceName("app.posato.Posato")
interface PosatoInstance : DBusInterface {
    @DBusMemberName("Show")
    fun show(activationToken: String)
}

/** One menu node: `(ia{sv}av)`, children as variants of the same structure. */
class MenuLayout(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/** GetLayout's two results, the layout revision and the root node; dbus-java reads their types from the type arguments. */
class MenuLayoutReply<R, L>(
    @field:Position(0) @JvmField val revision: R,
    @field:Position(1) @JvmField val layout: L,
) : Tuple()

/** One entry of GetGroupProperties: `(ia{sv})`. */
class MenuItemProperties(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

/** The tooltip: `(sa(iiay)ss)`, an icon name, no pixmaps, a title, and a description. */
class ToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val iconPixmaps: List<IconPixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val description: String,
) : Struct()

/** One ARGB32 image in network byte order: `(iiay)`. */
class IconPixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val bytes: ByteArray,
) : Struct()
