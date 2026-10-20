package app.posato.control.cli

import app.posato.control.android.AndroidDevice
import app.posato.control.desktop.ApplicationObservation
import app.posato.control.desktop.Observer
import app.posato.control.desktop.PageOutcome
import app.posato.control.desktop.WebsiteObservation
import app.posato.control.linux.LinuxAccessibility
import app.posato.control.linux.LinuxGuest
import app.posato.control.vm.shellQuote

private val PING_ADDRESS = Regex("""^PING \S+ \(([^)]+)\)""")
private val UNROUTABLE = setOf("0.0.0.0", "127.0.0.1", "::", "::1")

/** The address in `ping`'s first line, or the first field of `getent ahosts`; null when the name did not resolve. */
internal fun lookupAddress(output: String): String? {
    val first = output.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    PING_ADDRESS.find(first)?.let { return it.groupValues[1] }
    val field = first.split(Regex("\\s+")).first()
    val ipv4 = field.contains('.') && field.all { it.isDigit() || it == '.' }
    val ipv6 = field.contains(':') && field.all { it == ':' || it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    return field.takeIf { ipv4 || ipv6 }
}

/** A pause answers a name with an unroutable address; any other address is the site itself. */
internal fun nameOutcome(address: String?): String = when (address) {
    null -> PageOutcome.UNREACHABLE
    in UNROUTABLE -> PageOutcome.PAUSED
    else -> PageOutcome.LOADED
}.name.lowercase()

private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore(':')

/**
 * Android: a website by the address its name resolves to through Posato's DNS-only VPN, and an application by
 * whether the block screen covers it after it opens. [name] is the application's package.
 */
class AndroidObserver(
    private val device: AndroidDevice,
) : Observer {
    override fun website(url: String): WebsiteObservation {
        val address = lookupAddress(device.shell("ping -c 1 -W 2 ${shellQuote(hostOf(url))} 2>&1 | head -1").stdout)
        return WebsiteObservation(url, proxied = false, status = 0, outcome = nameOutcome(address), address = address)
    }

    override fun application(
        name: String,
        seconds: Int,
    ): ApplicationObservation {
        device.shell("monkey -p ${shellQuote(name)} -c android.intent.category.LAUNCHER 1")
        Thread.sleep(seconds * MILLIS_PER_SECOND)
        val covered = device.snapshot().flatten().any { it.label == BLOCK_SCREEN_TITLE }
        device.input.key("KEYCODE_HOME")
        return ApplicationObservation(name, name, seconds, running = !covered)
    }

    private companion object {
        const val BLOCK_SCREEN_TITLE = "Paused by Posato"
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * Linux: a website by the address `getent` returns from `/etc/hosts`, and an application by whether a program started
 * from [name] (its path, such as /usr/bin/gnome-calculator) still runs after [seconds].
 */
class LinuxObserver(
    private val guest: LinuxGuest,
) : Observer {
    override fun website(url: String): WebsiteObservation {
        val address = lookupAddress(guest.exec("getent ahosts ${shellQuote(hostOf(url))}").stdout)
        return WebsiteObservation(url, proxied = false, status = 0, outcome = nameOutcome(address), address = address)
    }

    override fun application(
        name: String,
        seconds: Int,
    ): ApplicationObservation {
        val path = shellQuote(name)
        // On another session bus the program would start its own AT-SPI bus and hide Posato's tree from later reads.
        guest.exec("export DBUS_SESSION_BUS_ADDRESS=\$(cat ${LinuxAccessibility.BUS_FILE}); nohup $path >/dev/null 2>&1 &")
        Thread.sleep(seconds * MILLIS_PER_SECOND)
        // A process's name is cut to 15 characters, so the program is found by the file its process runs.
        val running = guest.exec(
            "target=\$(readlink -f $path); for p in /proc/[0-9]*; do " +
                "[ \"\$(readlink \$p/exe 2>/dev/null)\" = \"\$target\" ] && echo \${p#/proc/}; done",
        ).stdout.lines().filter { it.isNotBlank() }
        running.forEach { pid -> guest.exec("kill $pid") }
        return ApplicationObservation(name, name, seconds, running = running.isNotEmpty())
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
