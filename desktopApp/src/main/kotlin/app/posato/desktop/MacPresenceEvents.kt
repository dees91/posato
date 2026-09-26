package app.posato.desktop

import app.posato.feature.onboarding.MacLoginItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal enum class PresenceEvent { MENU_OPENED, MENU_CLOSED, POWER_OFF }

internal object MacPresenceEvents {
    private val events = Channel<PresenceEvent>(Channel.UNLIMITED)
    private val actions = Channel<Int>(Channel.UNLIMITED)
    private val alertLock = Mutex()
    private val pendingAlerts = ConcurrentHashMap<Int, CompletableDeferred<Int>>()
    private val nextAlert = AtomicInteger()

    val menuEvents: Flow<PresenceEvent> = events.receiveAsFlow()
    val menuActions: Flow<Int> = actions.receiveAsFlow()

    suspend fun presentAlert(
        title: String,
        message: String,
        primary: String,
        secondary: String,
    ): Int {
        return alertLock.withLock {
            val request = nextAlert.incrementAndGet()
            val choice = CompletableDeferred<Int>()
            pendingAlerts[request] = choice
            try {
                MacPresenceNative.presentAlert(request, title, message, primary, secondary)
                choice.await()
            } finally {
                pendingAlerts.remove(request)
            }
        }
    }

    @JvmStatic
    fun onMenuOpened() {
        events.trySend(PresenceEvent.MENU_OPENED)
    }

    @JvmStatic
    fun onMenuClosed() {
        events.trySend(PresenceEvent.MENU_CLOSED)
    }

    @JvmStatic
    fun onMenuAction(action: Int) {
        actions.trySend(action)
    }

    @JvmStatic
    fun onPowerOff() {
        events.trySend(PresenceEvent.POWER_OFF)
    }

    @JvmStatic
    fun onAlertFinished(
        request: Int,
        choice: Int,
    ) {
        pendingAlerts[request]?.complete(choice)
    }
}

internal data class PresenceMenuItem(
    val title: String,
    val action: Int,
    val enabled: Boolean = true,
)

internal object MacLoginItemState : MacLoginItem {
    private val mutableEnabled = MutableStateFlow(false)

    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = MacPresenceNative.setLoginItem(enabled) == SERVICE_ENABLED
    }

    override fun refresh() {
        mutableEnabled.value = MacPresenceNative.loginItemStatus() == SERVICE_ENABLED
    }

    private const val SERVICE_ENABLED: Int = 1
}
