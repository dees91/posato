package app.posato.desktop.linux.status

import app.posato.desktop.PresenceMenuActionId
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * The status icon's menu over com.canonical.dbusmenu. A change of lines or actions reaches the panel at once, since
 * GNOME's AppIndicator extension keeps the layout it last read; a change of wording only (the minutes left) reaches
 * it at once only while the menu is open, and otherwise when the panel asks with AboutToShow, so an idle icon sends
 * nothing.
 */
internal class StatusMenu(
    private val entries: () -> List<StatusMenuEntry>,
    private val onAction: (PresenceMenuActionId) -> Unit,
    private val onOpened: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) : DbusMenu {
    @Volatile private var revision = 1L

    // Panels rarely say when the menu closes, so an opened menu counts as open only for a short while.
    @Volatile private var openUntil = 0L

    @Volatile private var stale = false

    /** The LayoutUpdated signal to send now, or null when only wording changed while the menu is closed. */
    fun layoutChanged(structural: Boolean): LayoutUpdated? {
        revision++
        if (!structural && clock() >= openUntil) {
            stale = true
            return null
        }
        return DbusMenu.LayoutUpdated(LinuxStatusArea.MENU_PATH, UInt32(revision), ROOT)
    }

    override val version: Variant<*> get() = Variant(UInt32(VERSION))

    override val textDirection: Variant<*> get() = Variant("ltr")

    override val status: Variant<*> get() = Variant("normal")

    override val iconThemePath: Variant<*> get() = Variant(emptyList<String>(), "as")

    override fun getLayout(
        parentId: Int,
        recursionDepth: Int,
        propertyNames: List<String>,
    ): MenuLayoutReply<UInt32, MenuLayout> {
        val children = entries().map { entry -> Variant(MenuLayout(entry.id, propertiesOf(entry), emptyList()), NODE) }
        val root = MenuLayout(ROOT, mapOf("children-display" to Variant("submenu")), if (parentId == ROOT) children else emptyList())
        return MenuLayoutReply(UInt32(revision), root)
    }

    override fun getGroupProperties(
        ids: List<Int>,
        propertyNames: List<String>,
    ): List<MenuItemProperties> = entries().filter { it.id in ids }.map { MenuItemProperties(it.id, propertiesOf(it)) }

    override fun getProperty(
        id: Int,
        name: String,
    ): Variant<*> = entries().firstOrNull { it.id == id }?.let { propertiesOf(it)[name] } ?: Variant("")

    override fun event(
        id: Int,
        eventId: String,
        data: Variant<*>,
        timestamp: UInt32,
    ) {
        when (eventId) {
            "opened" -> if (id == ROOT) openUntil = clock() + OPEN_WINDOW_MILLIS
            "closed" -> if (id == ROOT) openUntil = 0L
            "clicked" -> entries().firstOrNull { it.id == id && it.enabled }?.action?.let(onAction)
        }
    }

    override fun aboutToShow(id: Int): Boolean {
        if (id != ROOT) return false
        openUntil = clock() + OPEN_WINDOW_MILLIS
        onOpened()
        val update = stale
        stale = false
        return update
    }

    override fun getObjectPath(): String = LinuxStatusArea.MENU_PATH

    private fun propertiesOf(entry: StatusMenuEntry): Map<String, Variant<*>> {
        if (entry.separator) return mapOf("type" to Variant("separator"))
        return mapOf("label" to Variant(entry.label), "enabled" to Variant(entry.enabled), "visible" to Variant(true))
    }

    private companion object {
        const val ROOT = 0
        const val VERSION = 3L
        const val NODE = "(ia{sv}av)"
        const val OPEN_WINDOW_MILLIS = 120_000L
    }
}

private typealias LayoutUpdated = DbusMenu.LayoutUpdated
