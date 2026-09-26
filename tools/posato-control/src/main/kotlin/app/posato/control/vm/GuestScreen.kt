package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.desktop.AxBridgeBinary
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@Serializable
data class RecognizedLine(
    val text: String,
    val confidence: Double = 0.0,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    val centerX: Int
        get() = x + w / 2
    val centerY: Int
        get() = y + h / 2
}

/** Whether this line shows [text]: equal to it when [exact], otherwise containing it regardless of case. */
internal fun RecognizedLine.matches(
    text: String,
    exact: Boolean
): Boolean = if (exact) this.text.trim() == text else this.text.contains(text, ignoreCase = true)

internal fun parseRecognizedLines(json: String): List<RecognizedLine> =
    ControlJson.lenient.decodeFromString(ListSerializer(RecognizedLine.serializer()), json)

/**
 * The guest's screen seen through Virtualization's VNC server, located by text recognition on the host.
 * Frames are written to [scratch] only for recognition and deleted at once: they can show account details.
 */
class GuestScreen(
    private val context: RunContext,
    private val endpoint: VncEndpoint,
    private val scratch: Path,
) {
    /** Framebuffer width in pixels, read once from the server. */
    val width: Int by lazy { session { client -> client.width } }

    fun <T> session(block: (VncClient) -> T): T = VncClient.connect(endpoint.host, endpoint.port, endpoint.password).use(block)

    /** Captures over a fresh connection: several captures on one connection can stall the Virtualization server. */
    fun read(): List<RecognizedLine> {
        Files.createDirectories(scratch.parent)
        ImageIO.write(session { client -> client.capture() }, "png", scratch.toFile())
        try {
            AxBridgeBinary(context).ensureBuilt()
            val output = context.subprocess.run(listOf(context.layout.accessibilityBridgeBinary.toString(), "ocr", scratch.toString()))
                .requireSuccess(ErrorCode.COMMAND_FAILED, "Recognizing text on the guest screen")
            return parseRecognizedLines(output.stdout)
        } finally {
            Files.deleteIfExists(scratch)
        }
    }

    fun screenshot(file: Path): Path {
        Files.createDirectories(file.parent)
        ImageIO.write(session { client -> client.capture() }, "png", file.toFile())
        return file
    }

    /** Polls until a recognized line contains [text] (or equals it when [exact]); returns every match. */
    fun waitFor(
        text: String,
        timeoutMs: Long,
        exact: Boolean = false
    ): List<RecognizedLine> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val matches = read().filter { it.matches(text, exact) }
            if (matches.isNotEmpty()) return matches
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(
                    ErrorCode.WAIT_TIMEOUT,
                    "No text '$text' appeared on the guest screen within ${timeoutMs / MILLIS_PER_SECOND} s.",
                )
            }
            Thread.sleep(POLL_MS)
        }
    }

    fun waitGone(
        text: String,
        timeoutMs: Long,
        exact: Boolean = false
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (read().any { it.matches(text, exact) }) {
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(ErrorCode.WAIT_TIMEOUT, "The text '$text' stayed on the guest screen for ${timeoutMs / MILLIS_PER_SECOND} s.")
            }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
