package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.model.SnapshotNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

@Serializable
data class Permissions(
    val accessibility: Boolean,
    val screenRecording: Boolean,
)

@Serializable
data class WindowInfo(
    val id: Long,
    val layer: Int,
    val name: String? = null,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
)

@Serializable
data class StatusMenuItem(
    val title: String,
    val enabled: Boolean,
)

@Serializable
data class StatusMenuResult(
    val description: String,
    val items: List<StatusMenuItem>,
    val opened: Boolean? = null,
    val chosen: Boolean? = null,
)

class AxBridge(
    private val context: RunContext
) {
    private val layout = context.layout

    fun permissions(request: Boolean = false): Permissions =
        decode(Permissions.serializer(), invoke(if (request) "request-permissions" else "permissions"))

    fun windows(pid: Long): List<WindowInfo> = decode(WindowListSerializer, invoke("windows", pid.toString()))

    fun snapshot(
        pid: Long,
        maxDepth: Int?
    ): SnapshotNode = decode(SnapshotNode.serializer(), invoke("snapshot", pid.toString(), (maxDepth ?: DEFAULT_MAX_DEPTH).toString()))

    fun press(
        pid: Long,
        path: String
    ) {
        invoke("press", pid.toString(), path)
    }

    val windowActions = AxWindowActions { arguments -> invoke(*arguments) }

    fun type(
        pid: Long,
        path: String,
        text: String,
        clear: Boolean,
        submit: Boolean
    ): String {
        val encoded = Base64.getEncoder().encodeToString(text.toByteArray())
        val output = invoke("type", pid.toString(), path, encoded, if (clear) "1" else "0", if (submit) "1" else "0")
        return parseObject(output)["value"]?.jsonPrimitive?.content.orEmpty()
    }

    /** Types into whatever the addressed process has focused; the only path into a window with no element tree. */
    fun typeFocused(
        pid: Long,
        text: String,
        clear: Boolean,
        submit: Boolean
    ) {
        val encoded = Base64.getEncoder().encodeToString(text.toByteArray())
        invoke("type-focused", pid.toString(), encoded, if (clear) "1" else "0", if (submit) "1" else "0")
    }

    /** [sessionFallback] is set only when the caller addressed a process explicitly; see the bridge's routing rule. */
    fun key(
        pid: Long,
        key: String,
        modifiers: List<String>,
        sessionFallback: Boolean
    ) {
        invoke("key", pid.toString(), key, modifiers.joinToString(","), if (sessionFallback) "1" else "0")
    }

    fun statusMenu(
        pid: Long,
        mode: String,
        title: String?
    ): StatusMenuResult = decode(StatusMenuResult.serializer(), invoke("status-menu", pid.toString(), mode, title.orEmpty()))

    fun closeWindow(pid: Long) {
        invoke("close-window", pid.toString())
    }

    private val binary = AxBridgeBinary(context)

    private fun invoke(vararg arguments: String): String {
        binary.ensureBuilt()
        val output = context.subprocess.run(listOf(layout.accessibilityBridgeBinary.toString()) + arguments)
        if (output.exitCode != 0) throw bridgeFailure(output.stdout, output.stderr)
        return output.stdout
    }

    private fun bridgeFailure(
        stdout: String,
        stderr: String
    ): ControlException {
        val error = try {
            parseObject(stdout)["error"]?.jsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val code = error?.get("code")?.jsonPrimitive?.content
        val message = error?.get("message")?.jsonPrimitive?.content ?: stderr.trim().ifEmpty { "The accessibility bridge failed." }
        val mapped = ErrorCode.entries.firstOrNull { it.name == code } ?: ErrorCode.COMMAND_FAILED
        return ControlException(mapped, message)
    }

    private fun parseObject(output: String): JsonObject = ControlJson.lenient.parseToJsonElement(output).jsonObject

    private fun <T> decode(
        serializer: kotlinx.serialization.KSerializer<T>,
        output: String
    ): T = try {
        ControlJson.lenient.decodeFromString(serializer, output)
    } catch (exception: SerializationException) {
        throw ControlException(ErrorCode.COMMAND_FAILED, "Unreadable accessibility bridge output: ${exception.message}", cause = exception)
    }

    private companion object {
        const val DEFAULT_MAX_DEPTH = 64
        val WindowListSerializer = kotlinx.serialization.builtins.ListSerializer(WindowInfo.serializer())
    }
}
