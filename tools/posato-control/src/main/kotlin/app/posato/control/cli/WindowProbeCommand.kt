package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.core.refuseHostDesktop
import app.posato.control.core.runsInVirtualMachine
import app.posato.control.desktop.classifyPage
import app.posato.control.desktop.curlCommand
import app.posato.control.desktop.parseHttpProxy
import app.posato.control.desktop.splitCurlOutput
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One fresh load at one probe instant. */
@Serializable
data class WindowProbe(
    val boundaryEpochSeconds: Long,
    val offsetSeconds: Int,
    val url: String,
    /** When the load started, on the guest's clock. */
    val startedAtEpochMs: Long,
    val status: Int,
    /** `paused`, `loaded`, or `unreachable`, as `observe` reports. */
    val outcome: String,
    /** The pause page's window variant ("This site opens at"), English pages only. */
    val opensAtPage: Boolean,
)

/** Where a website's state first differed from its state at the earliest probe of a boundary, if it did. */
@Serializable
data class WindowTransition(
    val boundaryEpochSeconds: Long,
    val url: String,
    val before: String,
    val after: String? = null,
    /** The bounded interval (last probe before the change, first probe after it], on the guest's clock. */
    val lastBeforeEpochMs: Long? = null,
    val firstAfterEpochMs: Long? = null,
)

@Serializable
data class WindowProbeResult(
    val probes: List<WindowProbe>,
    val transitions: List<WindowTransition>,
)

/**
 * The TARGETS-013 measurement rule in a macOS guest: fresh loads through the system proxy, each with a cache-busting
 * query, at fixed instants around each boundary, timed by the guest's clock. It reports what it saw and asserts nothing;
 * the row's criteria are applied to the result.
 */
class WindowProbeCommand :
    ControlCommand(
        "window-probe",
        "Desktop in a VM: fresh loads through the system proxy at b-90, -60, -45, -30, -15, -3 s and every 15 s to " +
            "b+120 s around each window boundary.",
    ) {
    private val websites by option("--website", help = "http:// URL to load at every probe; repeat for the control.").multiple()
    private val boundaries by option(
        "--boundary",
        help = "A boundary as HH:MM today on the guest's clock, or epoch seconds; repeat for more.",
    ).multiple()

    override fun execute(session: Session): JsonElement {
        if (session.target() != Target.DESKTOP) {
            throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "window-probe drives the desktop target in a VM.")
        }
        refuseHostDesktop(Target.DESKTOP, hostAllowed = false, inVirtualMachine = ::runsInVirtualMachine)
        if (websites.isEmpty() || boundaries.isEmpty()) {
            throw ControlException(ErrorCode.USAGE, "window-probe needs --website and --boundary.")
        }
        val instants = boundaries.map(::epochSeconds).sorted()
        val probes = buildList {
            for (boundary in instants) {
                for (offset in OFFSETS) {
                    sleepUntil((boundary + offset) * MILLIS_PER_SECOND)
                    websites.forEach { url -> add(probe(session, boundary, offset, url)) }
                }
            }
        }
        val result = WindowProbeResult(probes, transitions(probes))
        return ControlJson.pretty.encodeToJsonElement(WindowProbeResult.serializer(), result)
    }

    private fun probe(
        session: Session,
        boundary: Long,
        offset: Int,
        url: String,
    ): WindowProbe {
        val proxy = parseHttpProxy(session.context.subprocess.run(listOf("/usr/sbin/scutil", "--proxy")).stdout)
        val started = System.currentTimeMillis()
        val separator = if ('?' in url) '&' else '?'
        val output = session.context.subprocess.run(
            curlCommand("$url${separator}probe=$started", proxy),
            timeout = Duration.ofSeconds(PROBE_TIMEOUT_SECONDS),
        )
        val (body, status) = splitCurlOutput(output.stdout)
        return WindowProbe(boundary, offset, url, started, status, classifyPage(status, body).name.lowercase(), OPENS_AT in body)
    }

    private fun transitions(probes: List<WindowProbe>): List<WindowTransition> {
        return probes.groupBy { it.boundaryEpochSeconds to it.url }.map { (key, series) ->
            val before = series.first().outcome
            val changed = series.indexOfFirst { it.outcome != before }
            if (changed < 0) {
                WindowTransition(key.first, key.second, before)
            } else {
                WindowTransition(
                    boundaryEpochSeconds = key.first,
                    url = key.second,
                    before = before,
                    after = series[changed].outcome,
                    lastBeforeEpochMs = series[changed - 1].startedAtEpochMs,
                    firstAfterEpochMs = series[changed].startedAtEpochMs,
                )
            }
        }
    }

    private fun epochSeconds(value: String): Long {
        value.toLongOrNull()?.let { return it }
        val time = runCatching { LocalTime.parse(value) }.getOrElse {
            throw ControlException(ErrorCode.USAGE, "--boundary takes HH:MM or epoch seconds, not $value.")
        }
        return LocalDate.now().atTime(time).atZone(ZoneId.systemDefault()).toEpochSecond()
    }

    private fun sleepUntil(epochMs: Long) {
        val remaining = epochMs - System.currentTimeMillis()
        if (remaining > 0) Thread.sleep(remaining)
    }

    private companion object {
        val OFFSETS = listOf(-90, -60, -45, -30, -15, -3, 0, 15, 30, 45, 60, 75, 90, 105, 120)
        const val MILLIS_PER_SECOND = 1_000L
        const val PROBE_TIMEOUT_SECONDS = 20L
        const val OPENS_AT = "This site opens at"
    }
}
