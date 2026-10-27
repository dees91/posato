package app.posato.desktop.windows

import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.PresenceMenuModel
import app.posato.desktop.resident.StatusSurface
import app.posato.desktop.resident.statusMark
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.awt.AWTException
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * Posato's icon in the Windows notification area, with the same menu as the Mac's, through AWT's tray. AWT does not
 * tell when the menu opens, so the resident core refreshes it on every state change and each minute of a pause. A
 * click on the icon, or a second launch, shows the window. Without a notification area the icon is not shown, and
 * closing the window follows the no-icon rules.
 */
internal class WindowsTray : StatusSurface {
    private val shows = Channel<Unit>(Channel.CONFLATED)
    private val menuActions = Channel<PresenceMenuActionId>(Channel.BUFFERED)
    private val shown = MutableStateFlow(false)
    private val menu = PopupMenu()
    private var icon: TrayIcon? = null
    private var filled = false
    private var lightTaskbar = false

    override val iconShown: StateFlow<Boolean> = shown
    override val showRequests: Flow<Unit> = shows.receiveAsFlow()
    override val actions: Flow<PresenceMenuActionId> = menuActions.receiveAsFlow()

    /** Puts the icon in the notification area; false when this session has none. */
    fun start(): Boolean {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return false
        val tray = SystemTray.getSystemTray()
        lightTaskbar = lightTaskbar()
        val trayIcon = TrayIcon(image(tray, filled = false), TOOLTIP, menu).apply {
            isImageAutoSize = true
            addActionListener { show() }
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (event.button == MouseEvent.BUTTON1 && event.clickCount == 1) show()
                    }
                },
            )
        }
        try {
            tray.add(trayIcon)
        } catch (_: AWTException) {
            return false
        }
        icon = trayIcon
        shown.value = true
        return true
    }

    /** A request from a second launch, which shows the window as the icon does. */
    fun show() {
        shows.trySend(Unit)
    }

    override fun update(model: PresenceMenuModel) {
        EventQueue.invokeLater {
            val trayIcon = icon ?: return@invokeLater
            rebuild(model)
            trayIcon.toolTip = model.accessibilityDescription.ifEmpty { TOOLTIP }.take(TOOLTIP_LENGTH)
            val light = lightTaskbar()
            if (model.filled != filled || light != lightTaskbar) {
                filled = model.filled
                lightTaskbar = light
                trayIcon.image = image(SystemTray.getSystemTray(), filled)
            }
        }
    }

    private fun rebuild(model: PresenceMenuModel) {
        menu.removeAll()
        model.items.forEach { item ->
            val action = PresenceMenuActionId.of(item.action)
            when {
                action != null -> menu.add(
                    MenuItem(item.title).apply {
                        isEnabled = item.enabled
                        addActionListener { menuActions.trySend(action) }
                    },
                )

                item.title.isEmpty() -> menu.addSeparator()

                else -> menu.add(MenuItem(item.title).apply { isEnabled = false })
            }
        }
    }

    /** The mark at the icon's size in device pixels, dark on a light taskbar and light on a dark one. */
    private fun image(
        tray: SystemTray,
        filled: Boolean,
    ): BufferedImage {
        val scale = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        val size = (tray.trayIconSize.width * scale).roundToInt().coerceAtLeast(MIN_ICON_PIXELS)
        return statusMark(size, filled, if (lightTaskbar) DARK_MARK else LIGHT_MARK)
    }

    /** Windows 11's taskbar follows the system theme (`SystemUsesLightTheme`), which is dark unless the person chose light. */
    private fun lightTaskbar(): Boolean {
        return runCatching {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, PERSONALIZE_KEY, LIGHT_THEME_VALUE) &&
                Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, PERSONALIZE_KEY, LIGHT_THEME_VALUE) == 1
        }.getOrDefault(false)
    }

    private companion object {
        const val TOOLTIP = "Posato"

        /** The notification area truncates longer tooltips. */
        const val TOOLTIP_LENGTH = 127
        const val MIN_ICON_PIXELS = 16
        const val LIGHT_MARK = 0xFFF2F2E9.toInt()
        const val DARK_MARK = 0xFF1C1C1A.toInt()
        const val PERSONALIZE_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
        const val LIGHT_THEME_VALUE = "SystemUsesLightTheme"
    }
}
