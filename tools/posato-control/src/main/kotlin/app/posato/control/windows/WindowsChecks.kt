package app.posato.control.windows

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.time.Duration

/**
 * The guest-side facts `PLATFORM-002`'s acceptance reads in the Windows clone, from `posato-checks.ps1`: whether a
 * connection to a host goes through, which processes run, the pausing service's state, Posato's filters in the Windows
 * Filtering Platform, the start-at-login value, and what a second, standard local user gets from the service's pipe.
 */
class WindowsChecks(
    private val guest: WindowsGuest,
) {
    fun connect(
        host: String,
        port: Int,
    ): JsonObject = check("connect") {
        put("host", host)
        put("port", port)
    }

    fun processes(name: String): JsonObject = check("process") { put("name", name) }

    fun service(name: String): JsonObject = check("service") { put("name", name) }

    fun filters(match: String): JsonObject = check("filters") { put("match", match) }

    fun runValue(name: String): JsonObject = check("run-value") { put("name", name) }

    /** Connects to the pipe, sends [line] when given, and reads one answer; a refusal is reported, not raised. */
    fun pipe(
        pipe: String,
        line: String?,
        asOtherUser: Boolean,
    ): JsonObject {
        val request = buildJsonObject {
            put("check", "pipe")
            put("pipe", pipe)
            line?.let { put("line", it) }
        }
        if (!asOtherUser) return guest.tools.check(request)
        val result = asOtherUser("& ${WindowsGuest.psQuote(WindowsTools.CHECKS)} -Request ${WindowsGuest.psQuote(request.toString())}")
        val output = (result["output"] as? JsonPrimitive)?.content.orEmpty()
        // Without the pipe check's own answer nothing is known about the pipe, so this fails instead of reading as refused.
        val answer = output.lines().lastOrNull { it.trim().startsWith("{") }
            ?: throw ControlException(ErrorCode.COMMAND_FAILED, "The pipe check as $OTHER_USER gave no answer: $result")
        return ControlJson.lenient.parseToJsonElement(answer) as? JsonObject
            ?: throw ControlException(ErrorCode.COMMAND_FAILED, "The pipe check as $OTHER_USER printed no JSON object: $output")
    }

    /**
     * Runs [script] as [OTHER_USER], a standard local user without administrator rights that the check creates in the
     * clone on first use, through a one-time scheduled task. The password is new for each call and travels only in the
     * request on standard input. The result holds the script's output and exit code.
     */
    fun asOtherUser(
        script: String,
        timeout: Duration = OTHER_USER_TIMEOUT,
    ): JsonObject {
        val password = "Pz" + ByteArray(PASSWORD_BYTES).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) } + "!9"
        val request = buildJsonObject {
            put("check", "other-user")
            put("user", OTHER_USER)
            put("password", password)
            put("script", script)
            put("timeoutSeconds", timeout.seconds)
        }
        return guest.tools.check(request, timeout.plusMinutes(1))
    }

    private fun check(
        name: String,
        fields: JsonObjectBuilder.() -> Unit,
    ): JsonObject = guest.tools.check(
        buildJsonObject {
            put("check", name)
            fields()
        },
    )

    companion object {
        /** The standard local user that plays a foreign caller; it exists only in the disposable clone. */
        const val OTHER_USER = "posato-other"
        private const val PASSWORD_BYTES = 12
        private val OTHER_USER_TIMEOUT: Duration = Duration.ofSeconds(90)
    }
}
