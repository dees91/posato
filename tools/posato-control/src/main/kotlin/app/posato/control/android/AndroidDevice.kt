package app.posato.control.android

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

/** One element of a UIAutomator dump: its text or content description and the center of its bounds. */
data class AndroidNode(
    val text: String,
    val x: Int,
    val y: Int,
    val clickable: Boolean,
)

/**
 * Posato on an Android emulator or phone (ADR 0010), reached with `adb`: installed from the debug APK, granted its
 * permissions with `appops` and `pm grant`, and driven through UIAutomator dumps and `input` events.
 */
class AndroidDevice(
    private val context: RunContext,
    serialOverride: String? = null,
) {
    val serial: String = serialOverride ?: context.configuration.value(ConfigurationKey.ANDROID_SERIAL) ?: DEFAULT_SERIAL
    private val adb: String = Paths.get(System.getProperty("user.home"), "Library/Android/sdk/platform-tools/adb").toString()

    val screen: AndroidScreen = AndroidScreen(this)

    fun adb(vararg arguments: String): ProcessOutput {
        return context.subprocess.run(listOf(adb, "-s", serial) + arguments)
    }

    fun shell(script: String): ProcessOutput {
        return adb("shell", script)
    }

    fun install(apk: Path) {
        if (!Files.isRegularFile(apk)) throw ControlException(ErrorCode.APP_NOT_STAGED, "No APK at $apk; run ./gradlew :androidApp:assembleDebug.")
        adb("install", "-r", "-g", apk.toString()).requireSuccess(ErrorCode.INSTALL_FAILED, "Installing Posato on $serial")
    }

    /** The grants a person gives in Settings, given the way the shell user can. */
    fun grant() {
        listOf("GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "ACTIVATE_VPN", "SCHEDULE_EXACT_ALARM", "MANAGE_EXTERNAL_STORAGE").forEach { op ->
            shell("appops set $PACKAGE $op allow").requireSuccess(ErrorCode.COMMAND_FAILED, "Granting $op")
        }
        shell("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS; dumpsys deviceidle whitelist +$PACKAGE")
    }

    fun launch() {
        shell("am start -W -n $PACKAGE/.MainActivity").requireSuccess(ErrorCode.COMMAND_FAILED, "Launching Posato on $serial")
    }

    fun terminate() {
        shell("am force-stop $PACKAGE")
    }

    fun reset() {
        shell("pm clear $PACKAGE")
    }

    fun screenshot(file: Path): Path {
        Files.createDirectories(file.parent)
        val process = ProcessBuilder(adb, "-s", serial, "exec-out", "screencap", "-p").redirectOutput(file.toFile()).start()
        process.waitFor()
        return file
    }

    companion object {
        const val PACKAGE = "app.posato.android"
        const val DEFAULT_SERIAL = "emulator-5560"
    }
}

/** What is on the device's screen, read from UIAutomator dumps, and the touches and keys that drive it. */
class AndroidScreen(
    private val device: AndroidDevice,
) {
    fun nodes(): List<AndroidNode> {
        val dump = device.shell("uiautomator dump /sdcard/posato-ui.xml >/dev/null && cat /sdcard/posato-ui.xml")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Reading the screen of ${device.serial}").stdout
        val xml = dump.substring(dump.indexOf("<?xml").coerceAtLeast(0))
        if (xml.isBlank()) return emptyList()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val elements = document.getElementsByTagName("node")
        return (0 until elements.length).mapNotNull { index ->
            val element = elements.item(index) as Element
            val text = element.getAttribute("text").ifEmpty { element.getAttribute("content-desc") }
            val bounds = BOUNDS.find(element.getAttribute("bounds"))?.groupValues?.drop(1)?.map(String::toInt) ?: return@mapNotNull null
            text.takeIf { it.isNotBlank() }?.let {
                AndroidNode(it, (bounds[LEFT] + bounds[RIGHT]) / 2, (bounds[TOP] + bounds[BOTTOM]) / 2, element.getAttribute("clickable") == "true")
            }
        }
    }

    fun waitFor(
        text: String,
        exact: Boolean,
        timeoutMillis: Long,
        scrolls: Boolean = false,
    ): List<AndroidNode> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var attempt = 0
        while (true) {
            val matches = nodes().filter { if (exact) it.text.trim() == text else it.text.contains(text, ignoreCase = true) }
            if (matches.isNotEmpty()) return matches
            // A control below or above the visible part of a screen is reached the way a person reaches it, by scrolling.
            if (scrolls) {
                if (attempt++ % SCROLL_CYCLE < SCROLLS_DOWN) {
                    swipeUp()
                } else {
                    swipeDown()
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(ErrorCode.WAIT_TIMEOUT, "\"$text\" did not appear on ${device.serial} within ${timeoutMillis / MILLIS} s.")
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    fun tap(node: AndroidNode) {
        device.shell("input tap ${node.x} ${node.y}").requireSuccess(ErrorCode.COMMAND_FAILED, "Tapping on ${device.serial}")
    }

    fun type(text: String) {
        val escaped = text.replace(" ", "%s").replace("'", "")
        device.shell("input text '$escaped'").requireSuccess(ErrorCode.COMMAND_FAILED, "Typing on ${device.serial}")
    }

    fun key(name: String) {
        device.shell("input keyevent $name").requireSuccess(ErrorCode.COMMAND_FAILED, "Pressing $name on ${device.serial}")
    }

    fun swipeUp() {
        device.shell("input swipe 540 1700 540 900 300")
    }

    fun swipeDown() {
        device.shell("input swipe 540 900 540 1700 300")
    }

    private companion object {
        const val POLL_MILLIS = 1_000L
        const val MILLIS = 1_000
        const val SCROLLS_DOWN = 4
        const val SCROLL_CYCLE = 8
        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3
        val BOUNDS = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")
    }
}
