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
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

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

    fun key(
        pid: Long,
        key: String,
        modifiers: List<String>
    ) {
        invoke("key", pid.toString(), key, modifiers.joinToString(","))
    }

    fun ensureBuilt() {
        val source = layout.accessibilityBridgeSource
        val binary = layout.accessibilityBridgeBinary
        if (!source.exists()) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "The accessibility bridge source is missing at ${layout.relativize(source)}.")
        }
        if (binary.exists() && binary.getLastModifiedTime() >= source.getLastModifiedTime()) return
        Files.createDirectories(binary.parent)
        context.log("Compiling the accessibility bridge")
        context.subprocess.run(listOf("/usr/bin/xcrun", "swiftc", "-O", "-o", binary.toString(), source.toString()))
            .requireSuccess(ErrorCode.BUILD_FAILED, "Compiling the accessibility bridge", "Install Xcode command line tools.")
    }

    private fun invoke(vararg arguments: String): String {
        ensureBuilt()
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
