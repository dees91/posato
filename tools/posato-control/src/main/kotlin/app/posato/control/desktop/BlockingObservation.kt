package app.posato.control.desktop

import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import kotlinx.serialization.Serializable
import java.time.Duration

data class HttpProxy(
    val host: String,
    val port: Int,
)

enum class PageOutcome { PAUSED, LOADED, UNREACHABLE }

@Serializable
data class WebsiteObservation(
    val url: String,
    val proxied: Boolean,
    val status: Int,
    val outcome: String,
)

@Serializable
data class ApplicationObservation(
    val name: String,
    val bundleId: String,
    val runningAfterSeconds: Int,
    val running: Boolean,
)

private val PROXY_KEY = Regex("""^\s*(HTTPEnable|HTTPProxy|HTTPPort)\s*:\s*(\S+)\s*$""")

/** The system HTTP proxy from `scutil --proxy`, where Posato's helper points browsers during a session. */
internal fun parseHttpProxy(output: String): HttpProxy? {
    val values = output.lines().mapNotNull { PROXY_KEY.find(it)?.destructured }.associate { (key, value) -> key to value }
    if (values["HTTPEnable"] != "1") return null
    val host = values["HTTPProxy"] ?: return null
    val port = values["HTTPPort"]?.toIntOrNull() ?: return null
    return HttpProxy(host, port)
}

/** The helper's pause page always leads with this headline, with or without the session's end time. */
private const val PAUSE_HEADLINE = "This site is paused"
private const val HTTP_OK_FIRST = 200
private const val HTTP_OK_LAST = 299
private const val CURL_SECONDS = 15L
private const val MAX_REDIRECTS = 5

/** Classifies the final response after redirects, the page a browser would show. */
internal fun classifyPage(
    status: Int,
    body: String
): PageOutcome = when {
    PAUSE_HEADLINE in body -> PageOutcome.PAUSED
    status in HTTP_OK_FIRST..HTTP_OK_LAST -> PageOutcome.LOADED
    else -> PageOutcome.UNREACHABLE
}

/** Follows a bounded redirect chain, as a browser would, and reports the final status after the body. */
internal fun curlCommand(
    url: String,
    proxy: HttpProxy?
): List<String> = buildList {
    addAll(listOf("/usr/bin/curl", "-s", "-L", "--max-redirs", MAX_REDIRECTS.toString(), "-m", CURL_SECONDS.toString()))
    addAll(listOf("-w", "\n%{http_code}"))
    proxy?.let { addAll(listOf("--proxy", "http://${it.host}:${it.port}")) }
    add(url)
}

/** `lsappinfo find bundleid=<id>` lists one application serial number per running instance, and nothing otherwise. */
internal fun isListed(lsappinfoOutput: String): Boolean = "ASN:" in lsappinfoOutput

/** `curl -w '\n%{http_code}'` appends the status on its own last line. */
internal fun splitCurlOutput(stdout: String): Pair<String, Int> {
    val cut = stdout.lastIndexOf('\n')
    val body = if (cut < 0) "" else stdout.substring(0, cut)
    return body to (stdout.substring(cut + 1).trim().toIntOrNull() ?: 0)
}

/**
 * Observes enforcement the way a person would meet it inside a macOS guest: a browser request through the system
 * proxy, and an application launch. It reads nothing from Posato itself, so it proves blocking, not app state.
 */
class BlockingObserver(
    private val context: RunContext
) {
    fun website(url: String): WebsiteObservation {
        val proxy = parseHttpProxy(context.subprocess.run(listOf("/usr/sbin/scutil", "--proxy")).stdout)
        val output = context.subprocess.run(curlCommand(url, proxy), timeout = Duration.ofSeconds(CURL_SECONDS + GRACE_SECONDS))
        val (body, status) = splitCurlOutput(output.stdout)
        return WebsiteObservation(url, proxy != null, status, classifyPage(status, body).name.lowercase())
    }

    /**
     * Opens the application and reports whether it still runs after [seconds]; a paused one is ended at launch. The
     * name is resolved to its bundle identifier first, because a display name and a process name can differ
     * (Firefox runs as `firefox`), and a name lookup would then report a running application as ended.
     */
    fun application(
        name: String,
        seconds: Int
    ): ApplicationObservation {
        val bundleId = context.subprocess
            .run(listOf("/usr/bin/osascript", "-e", "id of application \"$name\""))
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Resolving $name", "Name an installed application, such as Safari.")
            .stdout
            .trim()
        context.subprocess.run(listOf("/usr/bin/open", "-b", bundleId)).requireSuccess(ErrorCode.COMMAND_FAILED, "Opening $name")
        Thread.sleep(Duration.ofSeconds(seconds.toLong()).toMillis())
        val running = isListed(context.subprocess.run(listOf("/usr/bin/lsappinfo", "find", "bundleid=$bundleId")).stdout)
        if (running) context.subprocess.run(listOf("/usr/bin/osascript", "-e", "quit app id \"$bundleId\""))
        return ApplicationObservation(name, bundleId, seconds, running)
    }

    private companion object {
        const val GRACE_SECONDS = 5L
    }
}
