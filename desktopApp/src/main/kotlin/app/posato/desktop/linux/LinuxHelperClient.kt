package app.posato.desktop.linux

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** One line to the root service of ADR 0010 and its one-line answer, or null when the service is not there. */
internal class LinuxHelperClient(
    private val socket: Path = Paths.get("/run/posato/helper.sock"),
) {
    /** Whether the service answers; a socket file left by a stopped service does not count. */
    fun installed(): Boolean {
        return send("status")?.startsWith("ok") == true
    }

    fun send(request: String): String? {
        if (!Files.exists(socket)) return null
        return try {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(UnixDomainSocketAddress.of(socket))
                channel.write(ByteBuffer.wrap("$request\n".toByteArray(StandardCharsets.UTF_8)))
                val buffer = ByteBuffer.allocate(RESPONSE_BYTES)
                var open = true
                while (open && buffer.hasRemaining() && '\n'.code.toByte() !in buffer.array().copyOf(buffer.position())) {
                    open = channel.read(buffer) >= 0
                }
                String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).substringBefore('\n')
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val RESPONSE_BYTES = 4_096
    }
}

/**
 * Installs the service once through `pkexec`, which asks for the administrator password in the desktop's own dialog.
 * The setup script ships root-owned in the package beside the application.
 */
internal class LinuxHelperInstaller(
    private val client: LinuxHelperClient,
) {
    fun install(): Boolean {
        if (client.installed()) return true
        val setup = setupScript() ?: return false
        val user = System.getProperty("user.name").orEmpty()
        if (!user.matches(Regex("[a-z_][a-z0-9_-]{0,31}"))) return false
        return try {
            val process = ProcessBuilder("pkexec", "/bin/sh", setup.toString(), user).redirectErrorStream(true).start()
            process.inputStream.readAllBytes()
            process.waitFor() == 0 && waitForSocket()
        } catch (_: IOException) {
            false
        }
    }

    private fun waitForSocket(): Boolean {
        repeat(SOCKET_WAITS) {
            if (client.installed()) return true
            Thread.sleep(SOCKET_WAIT_MILLIS)
        }
        return client.installed()
    }

    private fun setupScript(): Path? {
        val launcher = System.getProperty("jpackage.app-path") ?: return null
        val script = Paths.get(launcher).parent.parent.resolve("lib/app/posato-helper-setup")
        return script.takeIf { Files.isRegularFile(it) }
    }

    private companion object {
        const val SOCKET_WAITS = 50
        const val SOCKET_WAIT_MILLIS = 200L
    }
}
