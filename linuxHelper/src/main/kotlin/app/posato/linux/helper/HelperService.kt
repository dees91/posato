package app.posato.linux.helper

import jdk.net.ExtendedSocketOptions
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The root service of ADR 0010 on Linux. It answers one request per connection from the person who installed it (or
 * root), keeps `/etc/hosts` and the state file in step, ends the chosen programs while a pause runs, and clears a pause
 * by itself at its end.
 */
internal class HelperService(
    private val paths: HelperPaths,
    private val person: Person,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var state = readState()

    fun serve() {
        Files.createDirectories(paths.socket.parent)
        Files.setPosixFilePermissions(paths.socket.parent, PosixFilePermissions.fromString("rwxr-xr-x"))
        Files.deleteIfExists(paths.socket)
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(paths.socket))
        Files.setPosixFilePermissions(paths.socket, PosixFilePermissions.fromString("rw-rw-rw-"))
        val timer = Executors.newSingleThreadScheduledExecutor()
        timer.scheduleWithFixedDelay(::tick, 0, TICK_MILLIS, TimeUnit.MILLISECONDS)
        while (true) {
            server.accept().use(::answer)
        }
    }

    fun cleanup() {
        synchronized(lock) {
            writeHosts(emptyList())
            Files.deleteIfExists(paths.state)
        }
    }

    fun handle(request: HelperRequest): String {
        return synchronized(lock) {
            when (request) {
                is HelperRequest.Apply -> {
                    writeHosts(request.hosts)
                    save(HelperState(request, state.expiredSessionId.takeUnless { it == request.sessionId }))
                    endChosenProcesses()
                    "ok"
                }

                HelperRequest.Clear -> {
                    writeHosts(emptyList())
                    save(state.copy(applied = null))
                    "ok"
                }

                HelperRequest.Status -> {
                    "ok\t${state.applied?.sessionId ?: "-"}\t${state.expiredSessionId ?: "-"}"
                }

                is HelperRequest.Acknowledge -> {
                    if (state.expiredSessionId == request.sessionId) save(state.copy(expiredSessionId = null))
                    "ok"
                }
            }
        }
    }

    private fun answer(channel: SocketChannel) {
        val response = try {
            val peer = channel.getOption(ExtendedSocketOptions.SO_PEERCRED)
            if (peer.user.name != person.name && peer.user.name != ROOT) {
                "error\tpeer"
            } else {
                HelperRequest.parse(readLine(channel))?.let(::handle) ?: "error\trequest"
            }
        } catch (_: IOException) {
            "error\tio"
        }
        channel.write(ByteBuffer.wrap("$response\n".toByteArray(StandardCharsets.UTF_8)))
    }

    private fun tick() {
        synchronized(lock) {
            val applied = state.applied ?: return
            if (now() >= applied.endEpochMillis) {
                writeHosts(emptyList())
                save(HelperState(null, applied.sessionId))
            } else {
                endChosenProcesses()
            }
        }
    }

    private fun endChosenProcesses() {
        val matchers = state.applied?.executables.orEmpty()
        if (matchers.isEmpty()) return
        ProcessHandle.allProcesses().forEach { process ->
            val pid = process.pid()
            val executable = runCatching { Files.readSymbolicLink(Paths.get("/proc/$pid/exe")).toString() }.getOrNull() ?: return@forEach
            val owner = runCatching { Files.getAttribute(Paths.get("/proc/$pid"), "unix:uid") as Int }.getOrNull() ?: return@forEach
            if (ProcessMatch.shouldEnd(executable, owner, person.uid, matchers)) process.destroyForcibly()
        }
    }

    private fun writeHosts(hosts: List<String>) {
        val existing = if (Files.exists(paths.hosts)) Files.readString(paths.hosts) else ""
        val rewritten = HostsBlock.rewrite(existing, hosts)
        if (rewritten != existing) writeAtomically(paths.hosts, rewritten, "rw-r--r--")
    }

    private fun save(next: HelperState) {
        state = next
        writeAtomically(paths.state, next.encode())
    }

    private fun readState(): HelperState {
        return runCatching { HelperState.decode(Files.readString(paths.state)) }.getOrDefault(HelperState.EMPTY)
    }

    private fun readLine(channel: SocketChannel): String {
        val buffer = ByteBuffer.allocate(MAX_REQUEST_BYTES)
        var open = true
        while (open && buffer.hasRemaining() && '\n'.code.toByte() !in buffer.array().copyOf(buffer.position())) {
            open = channel.read(buffer) >= 0
        }
        return String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).substringBefore('\n')
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val MAX_REQUEST_BYTES = 1_048_576
        const val ROOT = "root"
    }
}

internal data class Person(
    val name: String,
    val uid: Int,
)

internal data class HelperPaths(
    val socket: Path = Paths.get("/run/posato/helper.sock"),
    val state: Path = Paths.get("/var/lib/posato/state"),
    val hosts: Path = Paths.get("/etc/hosts"),
    val config: Path = Paths.get("/etc/posato/helper.conf"),
)
