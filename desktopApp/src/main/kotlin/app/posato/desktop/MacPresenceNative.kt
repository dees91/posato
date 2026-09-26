package app.posato.desktop

import java.io.File

internal object MacPresenceNative {
    init {
        val resourcesDirectory = checkNotNull(System.getProperty("compose.application.resources.dir"))
        System.load(File(resourcesDirectory, "native/libPosatoWindow.dylib").absolutePath)
    }

    fun updateMenu(model: PresenceMenuModel) {
        setMenu(
            model.items.map { it.title }.toTypedArray(),
            model.items.map { it.action }.toIntArray(),
            model.items.map { it.enabled }.toBooleanArray(),
            model.accessibilityDescription,
            model.filled,
        )
    }

    @JvmStatic
    external fun installLaunchProbe()

    @JvmStatic
    external fun launchedAtLogin(): Boolean

    @JvmStatic
    external fun start(closeWindowTitle: String): Boolean

    @JvmStatic
    external fun setMainWindowVisible(visible: Boolean)

    @JvmStatic
    external fun presentAlert(
        request: Int,
        title: String,
        message: String,
        primary: String,
        secondary: String,
    )

    @JvmStatic
    external fun loginItemStatus(): Int

    @JvmStatic
    external fun setLoginItem(enabled: Boolean): Int

    @JvmStatic
    private external fun setMenu(
        titles: Array<String>,
        actions: IntArray,
        enabled: BooleanArray,
        accessibility: String,
        filled: Boolean,
    )
}
