package app.posato.desktop

import app.posato.feature.onboarding.MacLoginItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

internal enum class PresenceEvent { MENU_OPENED, MENU_CLOSED, POWER_OFF }

internal object MacPresence {
    private val events = Channel<PresenceEvent>(Channel.UNLIMITED)
    private val actions = Channel<Int>(Channel.UNLIMITED)
    private val alertChoices = Channel<Int>(Channel.CONFLATED)

    val menuEvents: Flow<PresenceEvent> = events.receiveAsFlow()
    val menuActions: Flow<Int> = actions.receiveAsFlow()

    init {
        val resourcesDirectory = checkNotNull(System.getProperty("compose.application.resources.dir"))
        System.load(File(resourcesDirectory, "native/libPosatoWindow.dylib").absolutePath)
    }

    fun installLaunchProbe() {
        nativeInstallLaunchProbe()
    }

    fun launchedAtLogin(): Boolean {
        return nativeLaunchedAtLogin()
    }

    fun start(closeWindowTitle: String): Boolean {
        return nativeStart(closeWindowTitle)
    }

    fun updateMenu(
        items: List<PresenceMenuItem>,
        accessibilityDescription: String,
        filled: Boolean,
    ) {
        nativeUpdateMenu(
            items.map { it.title }.toTypedArray(),
            items.map { it.action }.toIntArray(),
            items.map { it.enabled }.toBooleanArray(),
            accessibilityDescription,
            filled,
        )
    }

    fun setMainWindowVisible(visible: Boolean) {
        nativeSetMainWindowVisible(visible)
    }

    suspend fun presentAlert(
        title: String,
        message: String,
        primary: String,
        secondary: String,
    ): Int {
        nativePresentAlert(title, message, primary, secondary)
        return alertChoices.receive()
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
    fun onAlertFinished(choice: Int) {
        alertChoices.trySend(choice)
    }

    @JvmStatic
    private external fun nativeInstallLaunchProbe()

    @JvmStatic
    private external fun nativeLaunchedAtLogin(): Boolean

    @JvmStatic
    private external fun nativeStart(closeWindowTitle: String): Boolean

    @JvmStatic
    private external fun nativeUpdateMenu(
        titles: Array<String>,
        actions: IntArray,
        enabled: BooleanArray,
        accessibility: String,
        filled: Boolean,
    )

    @JvmStatic
    private external fun nativeSetMainWindowVisible(visible: Boolean)

    @JvmStatic
    private external fun nativePresentAlert(
        title: String,
        message: String,
        primary: String,
        secondary: String,
    )

    @JvmStatic
    external fun nativeLoginItemStatus(): Int

    @JvmStatic
    external fun nativeSetLoginItem(enabled: Boolean): Int
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
        mutableEnabled.value = MacPresence.nativeSetLoginItem(enabled) == SERVICE_ENABLED
    }

    override fun refresh() {
        mutableEnabled.value = MacPresence.nativeLoginItemStatus() == SERVICE_ENABLED
    }

    private const val SERVICE_ENABLED: Int = 1
}
