package app.posato.desktop.windows

import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * A second launch's request to show the running Posato's window, over an AF_UNIX socket in the user's Posato folder.
 * Only the Posato that holds the instance lock listens, so it replaces a socket file that a crash left behind; the
 * folder is the user's own, and the request carries nothing but `show`.
 */
internal class ShowRequests(
    private val socket: Path,
) : Closeable {
    private var server: ServerSocketChannel? = null

    /** Listens on a daemon thread and calls [onShow] for each request; false when the socket cannot be bound. */
    fun listen(onShow: () -> Unit): Boolean {
        val channel = try {
            Files.deleteIfExists(socket)
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(socket)) }
        } catch (_: IOException) {
            return false
        }
        server = channel
        Thread({ serve(channel, onShow) }, "posato-show-requests").apply { isDaemon = true }.start()
        return true
    }

    private fun serve(
        channel: ServerSocketChannel,
        onShow: () -> Unit,
    ) {
        while (channel.isOpen) {
            val client = try {
                channel.accept()
            } catch (_: IOException) {
                return
            }
            client.use { if (readRequest(it) == SHOW) onShow() }
        }
    }

    private fun readRequest(client: SocketChannel): String? {
        val buffer = ByteBuffer.allocate(REQUEST_BYTES)
        return try {
            while (buffer.hasRemaining() && client.read(buffer) >= 0) {
                if (buffer.position() > 0 && buffer.get(buffer.position() - 1) == NEWLINE) break
            }
            String(buffer.array(), 0, buffer.position(), StandardCharsets.US_ASCII).trim()
        } catch (_: IOException) {
            null
        }
    }

    override fun close() {
        runCatching { server?.close() }
        runCatching { Files.deleteIfExists(socket) }
    }

    companion object {
        private const val SHOW = "show"
        private const val NEWLINE = '\n'.code.toByte()
        private const val REQUEST_BYTES = 16

        /** Asks the running Posato to show its window; false when no Posato listens. */
        fun forward(socket: Path): Boolean {
            return try {
                SocketChannel.open(UnixDomainSocketAddress.of(socket)).use { client ->
                    client.write(ByteBuffer.wrap("$SHOW\n".toByteArray(StandardCharsets.US_ASCII)))
                    true
                }
            } catch (_: IOException) {
                false
            }
        }
    }
}
