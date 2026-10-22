package app.posato.control.android

import app.posato.control.core.ControlException
import app.posato.control.core.RunContext
import app.posato.control.model.DoctorCheck
import app.posato.control.model.Severity
import java.nio.file.Files

/**
 * What decides whether a run on the Android target can prove anything: a reachable, supported device, the APK, and
 * the device settings that change website pausing (another app's VPN, strict private DNS) or need a person (a screen
 * lock after a reboot).
 */
class AndroidDoctor(
    private val context: RunContext,
    private val device: AndroidDevice,
) {
    fun checks(): List<DoctorCheck> {
        val reachable = reachability()
        if (!reachable.ok) return listOf(adb(), reachable, apk())
        return listOf(adb(), reachable, version(), apk(), installed(), foreignVpn(), privateDns(), screenLock(), appearance())
    }

    private fun adb(): DoctorCheck = if (Files.isExecutable(java.nio.file.Paths.get(AndroidDevice.ADB))) {
        DoctorCheck.pass("android.adb", "adb is available.")
    } else {
        DoctorCheck.fail("android.adb", "No adb at ${AndroidDevice.ADB}.", "Install the Android SDK platform tools, or set ANDROID_HOME.")
    }

    private fun reachability(): DoctorCheck = try {
        device.requireReady()
        val kind = if (device.serial.startsWith("emulator-")) "emulator" else "phone"
        DoctorCheck.pass(
            "android.connected",
            "Connected: ${device.property("ro.product.model")} ($kind) on Android ${device.property("ro.build.version.release")}.",
        )
    } catch (exception: ControlException) {
        DoctorCheck.fail("android.connected", exception.message ?: "No device.", exception.hint)
    }

    private fun version(): DoctorCheck {
        val sdk = device.property("ro.build.version.sdk").toIntOrNull() ?: 0
        return if (sdk >= MIN_SDK) {
            DoctorCheck.pass("android.version", "API level $sdk is supported.")
        } else {
            DoctorCheck.fail("android.version", "API level $sdk is below Posato's minimum, $MIN_SDK (Android 13).")
        }
    }

    private fun apk(): DoctorCheck = if (Files.isRegularFile(device.apk)) {
        DoctorCheck.pass("android.apk", "The debug APK is built.")
    } else {
        DoctorCheck.fail("android.apk", "The debug APK is not built.", "Run `build -t android`.", Severity.WARN)
    }

    private fun installed(): DoctorCheck = if (device.app.installed()) {
        DoctorCheck.pass("android.installed", "Posato is installed.")
    } else {
        DoctorCheck.fail("android.installed", "Posato is not installed.", "Run `install -t android`.", Severity.WARN)
    }

    /** Android runs one VPN at a time, so starting a website pause takes over another app's VPN and turns it off. */
    private fun foreignVpn(): DoctorCheck {
        val owners = device.shell("dumpsys connectivity | grep 'Transports: VPN' | grep -o 'OwnerUid: [0-9]*' | sort -u").stdout
            .lines().mapNotNull { it.substringAfter("OwnerUid: ", "").trim().toIntOrNull() }
        val packages = owners.flatMap { uid ->
            device.shell("cmd package list packages -U | grep ' uid:$uid\$'").stdout.lines()
                .mapNotNull { it.substringAfter("package:", "").substringBefore(' ').ifBlank { null } }
        }.filter { it != AndroidDevice.PACKAGE }
        return if (packages.isEmpty()) {
            DoctorCheck.pass("android.vpn", "No other app's VPN is active.")
        } else {
            DoctorCheck.fail(
                "android.vpn",
                "Another app's VPN is active (${packages.joinToString()}); a website pause turns it off.",
                "Turn that VPN off for the run, and back on afterwards.",
                Severity.WARN,
            )
        }
    }

    /** Strict private DNS and a browser's own DNS over HTTPS bypass a DNS-only VPN by design. */
    private fun privateDns(): DoctorCheck {
        val mode = device.shell("settings get global private_dns_mode").stdout.trim()
        return if (mode == "hostname") {
            DoctorCheck.fail(
                "android.privateDns",
                "Private DNS is set to a provider; website pausing cannot answer names.",
                "Set Private DNS to Automatic or Off.",
                Severity.WARN,
            )
        } else {
            DoctorCheck.pass("android.privateDns", "Private DNS leaves name lookups to the VPN.")
        }
    }

    private fun screenLock(): DoctorCheck {
        val disabled = device.shell("locksettings get-disabled").stdout.trim()
        return if (disabled == "true" || device.serial.startsWith("emulator-")) {
            DoctorCheck.pass("android.screenLock", "No screen lock holds Posato back after a reboot.")
        } else {
            DoctorCheck.fail(
                "android.screenLock",
                "A screen lock is set: after a reboot Posato runs only once a person unlocks the phone.",
                "A reboot scenario needs one unlock by a person.",
                Severity.INFO,
            )
        }
    }

    /**
     * The mode outlives a run and decides what a capture shows, so a run that forgot to restore it shows here; read as
     * `appearance -t android` reads it.
     */
    private fun appearance(): DoctorCheck = try {
        DoctorCheck.pass("android.appearance", "The system appearance is ${device.nightMode()}.")
    } catch (exception: ControlException) {
        DoctorCheck.fail("android.appearance", exception.message ?: "The night mode is unreadable.", "Run `appearance -t android`.", Severity.INFO)
    }

    private companion object {
        const val MIN_SDK = 33
    }
}
