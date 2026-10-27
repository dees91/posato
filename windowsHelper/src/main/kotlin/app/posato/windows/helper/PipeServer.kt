package app.posato.windows.helper

import com.sun.jna.Memory
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import kotlin.system.exitProcess

/**
 * `\\.\pipe\app.posato.helper`: one request line and one answer per connection. The pipe is created as the first
 * instance, refuses remote clients, and lets only the system and the installing user connect, without the right to
 * create another instance; each connection's caller is then checked again through its own token.
 */
internal class PipeServer(
    private val userSid: String,
    private val workers: ExecutorService,
    private val handle: (String) -> String,
) {
    private val descriptor = SecurityDescriptor("O:SYG:SYD:P(A;;GA;;;SY)(A;;$CLIENT_RIGHTS;;;$userSid)")

    @Volatile
    private var running = true

    /** Serves until [stop]; returns false when another process already holds the pipe name. */
    fun serve(): Boolean {
        var pending = create(first = true) ?: return false
        while (running) {
            val client = pending
            val connected = Overlapped(client).use { overlapped ->
                overlapped.run(Win32.INFINITE) { Kernel32Extra.INSTANCE.connectNamedPipe(client, it) } != null
            }
            if (!running) break
            pending = waitForInstance()
            if (connected) workers.execute { answer(client) } else Kernel32.INSTANCE.CloseHandle(client)
        }
        Kernel32.INSTANCE.CloseHandle(pending)
        return true
    }

    fun stop() {
        running = false
        // A connection unblocks the pending accept so the loop can see that it should end.
        runCatching {
            Kernel32.INSTANCE.CreateFile(NAME, WinNT.GENERIC_READ, 0, null, WinNT.OPEN_EXISTING, 0, null)?.let(Kernel32.INSTANCE::CloseHandle)
        }
    }

    private fun waitForInstance(): WinNT.HANDLE {
        while (true) {
            create(first = false)?.let { return it }
            Thread.sleep(INSTANCE_RETRY_MILLIS)
        }
    }

    private fun create(first: Boolean): WinNT.HANDLE? {
        val openMode = PIPE_ACCESS_DUPLEX or FILE_FLAG_OVERLAPPED or (if (first) FILE_FLAG_FIRST_PIPE_INSTANCE else 0)
        val pipe = Kernel32Extra.INSTANCE.createNamedPipeW(
            WString(NAME),
            openMode,
            PIPE_REJECT_REMOTE_CLIENTS,
            MAX_INSTANCES,
            BUFFER_BYTES,
            BUFFER_BYTES,
            0,
            descriptor.attributes(),
        )
        return pipe.takeUnless { it == WinBase.INVALID_HANDLE_VALUE }
    }

    /** Any failure of one connection, such as a client that left before the answer, ends only that connection. */
    private fun answer(pipe: WinNT.HANDLE) {
        try {
            val line = readLine(pipe)
            val response = when {
                line == null -> null
                !PipeCaller.isAllowed(callerSid(pipe), userSid) -> "error\tpeer"
                else -> handle(line)
            }
            response?.let { write(pipe, "$it\n") }
        } finally {
            Kernel32.INSTANCE.DisconnectNamedPipe(pipe)
            Kernel32.INSTANCE.CloseHandle(pipe)
        }
    }

    /** Reads up to the first line break within the request deadline; null when the client is too slow or leaves. */
    private fun readLine(pipe: WinNT.HANDLE): String? {
        val deadline = System.nanoTime() + REQUEST_DEADLINE_NANOS
        val received = ByteArrayOutputStream()
        val buffer = Memory(BUFFER_BYTES.toLong())
        var line: String? = null
        var open = true
        Overlapped(pipe).use { overlapped ->
            while (open && line == null && received.size() < MAX_REQUEST_BYTES) {
                val remaining = ((deadline - System.nanoTime()) / NANOS_PER_MILLI).toInt()
                val count = if (remaining > 0) {
                    overlapped.run(remaining) { Kernel32Extra.INSTANCE.readFile(pipe, buffer, BUFFER_BYTES, null, it) }
                } else {
                    null
                }
                val bytes = count?.takeIf { it > 0 }?.let { buffer.getByteArray(0, it) }
                val end = bytes?.indexOf(NEWLINE) ?: -1
                when {
                    bytes == null -> open = false
                    end >= 0 -> line = received.apply { write(bytes, 0, end) }.toString(StandardCharsets.UTF_8)
                    else -> received.write(bytes)
                }
            }
        }
        return line
    }

    private fun write(
        pipe: WinNT.HANDLE,
        text: String,
    ) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val buffer = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
        Overlapped(pipe).use { overlapped ->
            overlapped.run(REQUEST_DEADLINE_MILLIS) { Kernel32Extra.INSTANCE.writeFile(pipe, buffer, bytes.size, null, it) }
        }
        Kernel32.INSTANCE.FlushFileBuffers(pipe)
    }

    /**
     * The caller's account, read from its identification token. The client opens the pipe at the identification
     * level, so the service can read who it is but never act as it.
     */
    private fun callerSid(pipe: WinNT.HANDLE): String? {
        if (!Advapi32Extra.INSTANCE.impersonateNamedPipeClient(pipe)) return null
        val token = WinNT.HANDLEByReference()
        val opened = try {
            Advapi32.INSTANCE.OpenThreadToken(Kernel32.INSTANCE.GetCurrentThread(), WinNT.TOKEN_QUERY, true, token)
        } finally {
            // A worker that cannot drop the caller's identity must not serve anyone else.
            if (!Advapi32.INSTANCE.RevertToSelf()) exitProcess(REVERT_FAILED)
        }
        if (!opened) return null
        return try {
            runCatching { Advapi32Util.getTokenAccount(token.value).sidString }.getOrNull()
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.value)
        }
    }

    private companion object {
        const val NAME = "\\\\.\\pipe\\app.posato.helper"

        /** Read, write, and synchronize, without `FILE_CREATE_PIPE_INSTANCE`. */
        const val CLIENT_RIGHTS = "0x0012018b"
        const val PIPE_ACCESS_DUPLEX = 0x3
        const val FILE_FLAG_OVERLAPPED = 0x40000000
        const val FILE_FLAG_FIRST_PIPE_INSTANCE = 0x00080000
        const val PIPE_REJECT_REMOTE_CLIENTS = 0x8
        const val MAX_INSTANCES = 8
        const val BUFFER_BYTES = 4_096
        const val MAX_REQUEST_BYTES = 1_048_576
        const val REQUEST_DEADLINE_MILLIS = 5_000
        const val REQUEST_DEADLINE_NANOS = 5_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val INSTANCE_RETRY_MILLIS = 100L
        const val NEWLINE = '\n'.code.toByte()
        const val REVERT_FAILED = 3
    }
}
