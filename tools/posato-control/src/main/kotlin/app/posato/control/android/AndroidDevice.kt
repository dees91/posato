package app.posato.control.android

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import app.posato.control.model.Appearances
import app.posato.control.model.SnapshotNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Which grants `install` gives the way the shell user can. A person gives the Settings-page grants (usage access,
 * display over other apps, exact alarms, all-files access) by switching them on, and answers the VPN, notification,
 * and battery requests in dialogs that onboarding raises; `SETTINGS` leaves those dialogs to onboarding.
 */
enum class AndroidGrants(
    val id: String,
) {
    ALL("all"),
    SETTINGS("settings"),
    NONE("none"),
    ;

    companion object {
        val choices: Map<String, AndroidGrants> = entries.associateBy { it.id }
    }
}

/**
 * An Android phone or emulator (ADR 0010), reached with `adb`: its screen read through UIAutomator, Posato on it as
 * [app], and touches, text, and keys through [input].
 */
class AndroidDevice(
    private val context: RunContext,
    serialOverride: String? = null,
) {
    val serial: String = serialOverride ?: context.configuration.value(ConfigurationKey.ANDROID_SERIAL) ?: DEFAULT_SERIAL

    val apk: Path = context.layout.root.resolve("androidApp/build/outputs/apk/debug/androidApp-debug.apk")

    val app: AndroidApp = AndroidApp(this, apk, context)

    val input: AndroidInput = AndroidInput(this)

    fun adb(vararg arguments: String): ProcessOutput = context.subprocess.run(listOf(ADB, "-s", serial) + arguments)

    fun shell(script: String): ProcessOutput = adb("shell", script)

    /** `device`, `unauthorized`, `offline`, or null when adb does not list the serial. */
    fun state(): String? {
        val listed = context.subprocess.run(listOf(ADB, "devices")).stdout.lines()
        return listed.drop(1).map { it.trim().split(Regex("\\s+")) }.firstOrNull { it.firstOrNull() == serial }?.getOrNull(1)
    }

    fun requireReady() {
        when (state()) {
            "device" -> Unit

            "unauthorized" -> throw ControlException(
                ErrorCode.NO_CONNECTED_DEVICE,
                "$serial has not allowed USB debugging from this computer.",
                "Unlock the phone and answer \"Allow USB debugging?\" with Always allow.",
            )

            else -> throw ControlException(
                ErrorCode.NO_CONNECTED_DEVICE,
                "adb lists no device $serial.",
                "Connect the phone, or start Posato's emulator; pass --udid <serial> or set posato.android.serial.",
            )
        }
    }

    fun property(name: String): String = shell("getprop $name").stdout.trim()

    /** `light`, `dark`, or the raw `auto`/`custom` value `cmd uimode night` reports. */
    fun nightMode(): String {
        val output = shell("cmd uimode night")
        val value = output.stdout.substringAfter("Night mode:", "").trim()
        if (output.exitCode != 0 || value.isEmpty()) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "cmd uimode night did not report the night mode: ${output.stdout.trim()}")
        }
        return when (value) {
            "yes" -> Appearances.DARK
            "no" -> Appearances.LIGHT
            else -> value
        }
    }

    fun screenshot(file: Path): Path {
        Files.createDirectories(file.parent)
        val process = ProcessBuilder(ADB, "-s", serial, "exec-out", "screencap", "-p").redirectOutput(file.toFile()).start()
        process.waitFor()
        return file
    }

    /** The whole screen, system dialogs included; a dump taken mid-animation can fail once, so it is read again. */
    fun snapshot(): SnapshotNode {
        repeat(DUMP_ATTEMPTS - 1) {
            val output = shell(DUMP)
            if (output.succeeded && "<hierarchy" in output.stdout) return parseUiAutomatorDump(xmlOf(output.stdout))
            Thread.sleep(DUMP_RETRY_MILLIS)
        }
        val output = shell(DUMP).requireSuccess(ErrorCode.COMMAND_FAILED, "Reading the screen of $serial")
        return parseUiAutomatorDump(xmlOf(output.stdout))
    }

    private fun xmlOf(stdout: String): String = stdout.substring(stdout.indexOf("<?xml").coerceAtLeast(0))

    companion object {
        const val PACKAGE = "app.posato.android"
        const val DEFAULT_SERIAL = "emulator-5560"
        val ADB: String = (System.getenv("ANDROID_HOME") ?: Paths.get(System.getProperty("user.home"), "Library/Android/sdk").toString()) +
            "/platform-tools/adb"
        private const val DUMP = "uiautomator dump /sdcard/posato-ui.xml >/dev/null && cat /sdcard/posato-ui.xml"
        private const val DUMP_ATTEMPTS = 3
        private const val DUMP_RETRY_MILLIS = 500L
    }
}

/** Posato's package on the device: installed from the debug APK, granted, started, and stopped. */
class AndroidApp(
    private val device: AndroidDevice,
    private val apk: Path,
    private val context: RunContext,
) {
    fun installed(): Boolean = device.shell("pm path ${AndroidDevice.PACKAGE}").stdout.contains("package:")

    fun pid(): Long? = device.shell("pidof ${AndroidDevice.PACKAGE}").stdout.trim().split(' ').firstOrNull()?.toLongOrNull()

    fun install(
        grants: AndroidGrants,
        packageFile: Path? = null,
    ) {
        val file = packageFile ?: apk
        if (!Files.isRegularFile(file)) {
            throw ControlException(ErrorCode.APP_NOT_STAGED, "No APK at ${context.layout.relativize(file)}.", "Run `build -t android`.")
        }
        val runtimeGrants = if (grants == AndroidGrants.ALL) listOf("-g") else emptyList()
        val arguments = (listOf("install", "-r") + runtimeGrants + file.toString()).toTypedArray()
        var output = device.adb(*arguments)
        // A build signed with another key (debug versus release) cannot replace the installed one; remove it first.
        if (output.exitCode != 0 && SIGNATURE_MISMATCH in output.stdout + output.stderr) {
            uninstall()
            output = device.adb(*arguments)
        }
        output.requireSuccess(ErrorCode.INSTALL_FAILED, "Installing Posato on ${device.serial}")
        if (grants != AndroidGrants.NONE) grantOps(SETTINGS_OPS)
        if (grants == AndroidGrants.ALL) {
            grantOps(DIALOG_OPS)
            device.shell("pm grant ${AndroidDevice.PACKAGE} android.permission.POST_NOTIFICATIONS")
            device.shell("dumpsys deviceidle whitelist +${AndroidDevice.PACKAGE}")
        }
    }

    private fun grantOps(ops: List<String>) {
        ops.forEach { op ->
            device.shell("appops set ${AndroidDevice.PACKAGE} $op allow").requireSuccess(ErrorCode.COMMAND_FAILED, "Granting $op")
        }
    }

    fun uninstall() {
        device.adb("uninstall", AndroidDevice.PACKAGE)
    }

    fun launch() {
        device.shell("am start -W -n ${AndroidDevice.PACKAGE}/.MainActivity")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Launching Posato on ${device.serial}")
    }

    fun terminate() {
        device.shell("am force-stop ${AndroidDevice.PACKAGE}")
    }

    fun clearData() {
        device.shell("pm clear ${AndroidDevice.PACKAGE}").requireSuccess(ErrorCode.COMMAND_FAILED, "Clearing Posato's data on ${device.serial}")
    }

    private companion object {
        const val SIGNATURE_MISMATCH = "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
        val SETTINGS_OPS = listOf("GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "SCHEDULE_EXACT_ALARM", "MANAGE_EXTERNAL_STORAGE")
        val DIALOG_OPS = listOf("ACTIVATE_VPN")
    }
}

/** Touches, text, and keys sent with `input`, as a person's fingers and keyboard would. */
class AndroidInput(
    private val device: AndroidDevice,
) {
    fun tap(
        x: Double,
        y: Double,
    ) {
        device.shell("input tap ${x.toInt()} ${y.toInt()}").requireSuccess(ErrorCode.COMMAND_FAILED, "Tapping on ${device.serial}")
    }

    fun type(text: String) {
        device.shell("input text ${inputText(text)}").requireSuccess(ErrorCode.COMMAND_FAILED, "Typing on ${device.serial}")
    }

    fun key(vararg codes: String) {
        val command = if (codes.size == 1) "input keyevent ${codes[0]}" else "input keycombination ${codes.joinToString(" ")}"
        device.shell(command).requireSuccess(ErrorCode.COMMAND_FAILED, "Pressing ${codes.joinToString("+")} on ${device.serial}")
    }

    /** Whether the on-screen keyboard is up; it covers the lower part of the screen and takes swipes as typing. */
    fun keyboardShown(): Boolean =
        device.shell("dumpsys input_method | grep -c 'mInputShown=true'").stdout.trim().toIntOrNull()?.let { it > 0 } == true

    fun swipe(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
    ) {
        device.shell("input swipe $fromX $fromY $toX $toY $SWIPE_MILLIS").requireSuccess(ErrorCode.COMMAND_FAILED, "Swiping on ${device.serial}")
    }

    private companion object {
        const val SWIPE_MILLIS = 300
    }
}

/**
 * `input text` reads `%s` as a space and passes its argument through the device shell, so the text is quoted for that
 * shell. It types printable ASCII only and has no escape for `%`; other text would arrive changed, so it is refused.
 */
internal fun inputText(text: String): String {
    if (text.any { it.code !in PRINTABLE_ASCII || it == '%' }) {
        throw ControlException(
            ErrorCode.UNSUPPORTED_ON_TARGET,
            "adb input types printable ASCII without %; the text has other characters.",
            "Use such text on the android target.",
        )
    }
    return "'" + text.replace(" ", "%s").replace("'", "'\\''") + "'"
}

private val PRINTABLE_ASCII = 0x20..0x7E
