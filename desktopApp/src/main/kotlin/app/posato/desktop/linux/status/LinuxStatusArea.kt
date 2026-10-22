package app.posato.desktop.linux.status

import app.posato.desktop.PresenceMenuActionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.freedesktop.dbus.connections.IDisconnectCallback
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBus
import java.io.Closeable
import java.io.IOException

/** What the status icon shows: the menu, the icon's state, and the text a panel or screen reader reads. */
internal data class StatusAreaState(
    val entries: List<StatusMenuEntry>,
    val title: String,
    val description: String,
    val filled: Boolean,
)

/**
 * Posato's presence on the Linux session bus (LINUX-003): the name a second launch reaches, and the status icon with
 * its menu, registered with the panel's StatusNotifierWatcher whenever one runs. Without a session bus nothing is
 * exported and [start] answers false; without a watcher the item waits for one ([watcherPresent]).
 */
internal class LinuxStatusArea(
    private val icons: StatusIcons,
    private val onShow: (activationToken: String) -> Unit,
    private val onAction: (PresenceMenuActionId) -> Unit,
    private val onMenuOpened: () -> Unit,
    private val onBusLost: () -> Unit,
    private val sessionBusAddress: () -> String? = ::environmentBusAddress,
) : Closeable {
    @Volatile private var connection: DBusConnection? = null

    @Volatile private var closing = false
    private val itemName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
    private val mutableWatcherPresent = MutableStateFlow(false)

    @Volatile private var state = StatusAreaState(emptyList(), "Posato", "Posato", filled = false)
    private val item = StatusItem(icons, { state }, onShow)
    private val menu = StatusMenu({ state.entries }, onAction, onMenuOpened)

    /** Whether a panel shows status icons right now; followed through NameOwnerChanged, never latched. */
    val watcherPresent: StateFlow<Boolean> = mutableWatcherPresent

    fun start(): Boolean {
        val address = sessionBusAddress()?.takeIf { it.isNotBlank() } ?: return false
        val bus = try {
            DBusConnectionBuilder.forAddress(address).withShared(false).withDisconnectCallback(LostBus { if (!closing) onBusLost() }).build()
        } catch (_: DBusException) {
            return false
        } catch (_: RuntimeException) {
            // dbus-java reports a malformed or unreachable address with unchecked exceptions too.
            return false
        }
        connection = bus
        return try {
            bus.requestBusName(INSTANCE_NAME)
            bus.requestBusName(itemName)
            bus.exportObject(INSTANCE_PATH, Instance(onShow))
            bus.exportObject(ITEM_PATH, item)
            bus.exportObject(MENU_PATH, menu)
            bus.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
                if (signal.name == WATCHER_NAME) watcherChanged(signal.newOwner.isNotEmpty())
            }
            val daemon = bus.getRemoteObject(BUS_NAME, BUS_PATH, DBus::class.java)
            watcherChanged(daemon.NameHasOwner(WATCHER_NAME))
            true
        } catch (_: DBusException) {
            close()
            false
        } catch (_: RuntimeException) {
            close()
            false
        }
    }

    /** Shows [next]; the panel hears about it at once only when the menu is open or the icon itself changed. */
    fun update(next: StatusAreaState) {
        val previous = state
        state = next
        val bus = connection ?: return
        if (previous.filled != next.filled) bus.sendQuietly(StatusNotifierItem.NewIcon(ITEM_PATH))
        if (previous.title != next.title) bus.sendQuietly(StatusNotifierItem.NewTitle(ITEM_PATH))
        if (previous.description != next.description) bus.sendQuietly(StatusNotifierItem.NewToolTip(ITEM_PATH))
        if (previous.entries != next.entries) menu.layoutChanged()?.let(bus::sendQuietly)
    }

    override fun close() {
        closing = true
        connection?.let { bus -> runCatching { bus.disconnect() } }
        connection = null
        mutableWatcherPresent.value = false
    }

    private fun watcherChanged(present: Boolean) {
        mutableWatcherPresent.value = present
        if (!present) return
        val bus = connection ?: return
        runCatching {
            bus.getRemoteObject(WATCHER_NAME, WATCHER_PATH, StatusNotifierWatcher::class.java).registerStatusNotifierItem(itemName)
        }
    }

    private class LostBus(
        private val onBusLost: () -> Unit,
    ) : IDisconnectCallback {
        override fun disconnectOnError(exception: IOException) = onBusLost()

        override fun exceptionOnTerminate(exception: IOException) = onBusLost()
    }

    private class Instance(
        private val onShow: (String) -> Unit,
    ) : PosatoInstance {
        override fun show(activationToken: String) = onShow(activationToken)

        override fun getObjectPath(): String = INSTANCE_PATH
    }

    companion object {
        const val INSTANCE_NAME = "app.posato.Posato"
        const val INSTANCE_PATH = "/app/posato/Posato"
        const val ITEM_PATH = "/StatusNotifierItem"
        const val MENU_PATH = "/MenuBar"
        private const val WATCHER_NAME = "org.kde.StatusNotifierWatcher"
        private const val WATCHER_PATH = "/StatusNotifierWatcher"
        private const val BUS_NAME = "org.freedesktop.DBus"
        private const val BUS_PATH = "/org/freedesktop/DBus"

        /** A second launch: asks the running Posato to show its window; false when no session bus or Posato answers. */
        fun forwardShow(
            activationToken: String,
            sessionBusAddress: () -> String? = ::environmentBusAddress,
        ): Boolean {
            val address = sessionBusAddress()?.takeIf { it.isNotBlank() } ?: return false
            return runCatching {
                DBusConnectionBuilder.forAddress(address).withShared(false).build().use { bus ->
                    bus.getRemoteObject(INSTANCE_NAME, INSTANCE_PATH, PosatoInstance::class.java).show(activationToken)
                }
                true
            }.getOrDefault(false)
        }
    }
}

private fun DBusConnection.sendQuietly(signal: org.freedesktop.dbus.messages.DBusSignal) {
    runCatching { sendMessage(signal) }
}

/**
 * The session bus the desktop advertises to the processes of the login session, or the per-user bus systemd keeps
 * in the runtime directory when a launcher left the variable out; null without either.
 */
private fun environmentBusAddress(): String? {
    System.getenv("DBUS_SESSION_BUS_ADDRESS")?.takeIf { it.isNotBlank() }?.let { return it }
    val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.startsWith("/") } ?: return null
    val socket = java.nio.file.Path.of(runtime, "bus")
    return if (java.nio.file.Files.exists(socket)) "unix:path=$socket" else null
}
