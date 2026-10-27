package app.posato.desktop.windows

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.Winsvc
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * One line to the Windows service of ADR 0010 and its one-line answer, or null when the service is not there. Before
 * anything is sent, the pipe's server must be a process of the system running the binary that the Service Control
 * Manager registered for the service, so a pipe squatted while the service is down receives nothing. The pipe is
 * opened at the identification level, so the service can read who calls but never act as this user.
 */
internal class WindowsHelperClient {
    /** Whether the service answers; a registered service that is stopped does not count. */
    fun installed(): Boolean {
        return send("status")?.startsWith("ok") == true
    }

    /** Whether the service is registered, answering or not; a missing one means nothing is installed to hold a pause. */
    fun present(): Boolean {
        return serviceImage() != null
    }

    /** Sends one line; a service that does not answer within the timeout counts as not answering. */
    fun send(request: String): String? {
        val image = serviceImage() ?: return null
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        val pipe = open(deadline) ?: return null
        return try {
            if (isServer(pipe, image)) exchange(pipe, "$request\n", deadline) else null
        } finally {
            Kernel32.INSTANCE.CloseHandle(pipe)
        }
    }

    private fun open(deadline: Long): WinNT.HANDLE? {
        while (System.nanoTime() < deadline) {
            val pipe = Kernel32.INSTANCE.CreateFile(
                PIPE_NAME,
                WinNT.GENERIC_READ or WinNT.GENERIC_WRITE,
                0,
                null,
                WinNT.OPEN_EXISTING,
                FILE_FLAG_OVERLAPPED or SECURITY_SQOS_PRESENT or SECURITY_IDENTIFICATION,
                null,
            )
            if (pipe != WinBase.INVALID_HANDLE_VALUE) return pipe
            if (Native.getLastError() != ERROR_PIPE_BUSY) return null
            Kernel32.INSTANCE.WaitNamedPipe(PIPE_NAME, remainingMillis(deadline).coerceAtLeast(1))
        }
        return null
    }

    /** The server process must run [image] under the system account. */
    private fun isServer(
        pipe: WinNT.HANDLE,
        image: String,
    ): Boolean {
        val pid = IntByReference()
        if (!PipeApi.INSTANCE.getNamedPipeServerProcessId(pipe, pid)) return false
        val process = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid.value) ?: return false
        try {
            val buffer = CharArray(MAX_PATH_CHARS)
            val length = IntByReference(buffer.size)
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, length)) return false
            return String(buffer, 0, length.value).equals(image, ignoreCase = true) && owner(process) == SYSTEM_SID
        } finally {
            Kernel32.INSTANCE.CloseHandle(process)
        }
    }

    private fun owner(process: WinNT.HANDLE): String? {
        val token = WinNT.HANDLEByReference()
        if (!Advapi32.INSTANCE.OpenProcessToken(process, WinNT.TOKEN_QUERY, token)) return null
        return try {
            runCatching { Advapi32Util.getTokenAccount(token.value).sidString }.getOrNull()
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.value)
        }
    }

    private fun exchange(
        pipe: WinNT.HANDLE,
        line: String,
        deadline: Long,
    ): String? {
        val request = line.toByteArray(StandardCharsets.UTF_8)
        val buffer = Memory(maxOf(request.size, RESPONSE_BYTES).toLong())
        buffer.write(0, request, 0, request.size)
        val api = PipeApi.INSTANCE
        return PipeOperation(pipe).use { operation ->
            val written = operation.run(remainingMillis(deadline)) { api.writeFile(pipe, buffer, request.size, null, it) }
            if (written != request.size) return@use null
            val received = ByteArrayOutputStream()
            var answer: String? = null
            while (answer == null && received.size() < RESPONSE_BYTES) {
                val count = operation.run(remainingMillis(deadline)) { api.readFile(pipe, buffer, RESPONSE_BYTES, null, it) }
                if (count == null || count == 0) break
                received.write(buffer.getByteArray(0, count))
                answer = received.toString(StandardCharsets.UTF_8).takeIf { '\n' in it }?.substringBefore('\n')
            }
            answer
        }
    }

    /** The binary that the Service Control Manager starts for the service, from its quoted path. */
    private fun serviceImage(): String? {
        val manager = Advapi32.INSTANCE.OpenSCManager(null, null, Winsvc.SC_MANAGER_CONNECT) ?: return null
        try {
            val service = Advapi32.INSTANCE.OpenService(manager, SERVICE_NAME, SERVICE_QUERY_CONFIG) ?: return null
            try {
                val needed = IntByReference()
                ServiceConfigApi.INSTANCE.queryServiceConfigW(service, null, 0, needed)
                if (needed.value <= 0) return null
                val config = Memory(needed.value.toLong())
                if (!ServiceConfigApi.INSTANCE.queryServiceConfigW(service, config, needed.value, needed)) return null
                val command = config.getPointer(BINARY_PATH_OFFSET)?.getWideString(0) ?: return null
                return command.removePrefix("\"").substringBefore('"').takeIf { command.startsWith('"') }
            } finally {
                Advapi32.INSTANCE.CloseServiceHandle(service)
            }
        } finally {
            Advapi32.INSTANCE.CloseServiceHandle(manager)
        }
    }

    private fun remainingMillis(deadline: Long): Int = ((deadline - System.nanoTime()) / NANOS_PER_MILLI).toInt().coerceAtLeast(0)

    private companion object {
        const val PIPE_NAME = "\\\\.\\pipe\\app.posato.helper"
        const val SERVICE_NAME = "PosatoHelper"
        const val SYSTEM_SID = "S-1-5-18"
        const val FILE_FLAG_OVERLAPPED = 0x40000000
        const val SECURITY_SQOS_PRESENT = 0x00100000
        const val SECURITY_IDENTIFICATION = 0x00010000
        const val ERROR_PIPE_BUSY = 231
        const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
        const val SERVICE_QUERY_CONFIG = 0x0001
        const val MAX_PATH_CHARS = 32_768
        const val RESPONSE_BYTES = 4_096

        /** `QUERY_SERVICE_CONFIGW`: three DWORDs, then the binary path pointer on a 64-bit boundary. */
        const val BINARY_PATH_OFFSET = 16L
        const val TIMEOUT_NANOS = 5_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** The pipe and service calls that `jna-platform` lacks or binds with Java arrays, which overlapped I/O cannot use. */
internal interface PipeApi : StdCallLibrary {
    fun readFile(
        file: WinNT.HANDLE,
        buffer: Pointer,
        toRead: Int,
        read: IntByReference?,
        overlapped: Pointer,
    ): Boolean

    fun writeFile(
        file: WinNT.HANDLE,
        buffer: Pointer,
        toWrite: Int,
        written: IntByReference?,
        overlapped: Pointer,
    ): Boolean

    fun getOverlappedResult(
        file: WinNT.HANDLE,
        overlapped: Pointer,
        transferred: IntByReference,
        wait: Boolean,
    ): Boolean

    fun cancelIoEx(
        file: WinNT.HANDLE,
        overlapped: Pointer?,
    ): Boolean

    fun getNamedPipeServerProcessId(
        pipe: WinNT.HANDLE,
        pid: IntByReference,
    ): Boolean

    companion object {
        val INSTANCE: PipeApi by lazy { loadMapped("kernel32") }
    }
}

/** `QueryServiceConfigW`, which `jna-platform` does not bind. */
internal interface ServiceConfigApi : StdCallLibrary {
    fun queryServiceConfigW(
        service: Winsvc.SC_HANDLE,
        config: Pointer?,
        size: Int,
        needed: IntByReference,
    ): Boolean

    companion object {
        val INSTANCE: ServiceConfigApi by lazy { loadMapped("advapi32") }
    }
}

/** Kotlin names start in lower case; the mapper restores the exported name. */
private inline fun <reified T : Library> loadMapped(name: String): T {
    val mapper = FunctionMapper { _, method -> method.name.replaceFirstChar(Char::uppercaseChar) }
    return Native.load(name, T::class.java, mapOf(Library.OPTION_FUNCTION_MAPPER to mapper))
}

/** One overlapped operation that is cancelled and awaited when its deadline passes. */
private class PipeOperation(
    private val handle: WinNT.HANDLE,
) : AutoCloseable {
    private val event: WinNT.HANDLE = Kernel32.INSTANCE.CreateEvent(null, true, false, null)
    private val overlapped = WinBase.OVERLAPPED()

    fun run(
        timeoutMillis: Int,
        start: (Pointer) -> Boolean,
    ): Int? {
        Kernel32.INSTANCE.ResetEvent(event)
        overlapped.clear()
        overlapped.hEvent = event
        overlapped.write()
        val pointer = overlapped.pointer
        val pending = start(pointer) || Native.getLastError() == ERROR_IO_PENDING
        if (!pending) return null
        val transferred = IntByReference()
        if (Kernel32.INSTANCE.WaitForSingleObject(event, timeoutMillis) != WAIT_OBJECT_0) {
            PipeApi.INSTANCE.cancelIoEx(handle, pointer)
            PipeApi.INSTANCE.getOverlappedResult(handle, pointer, transferred, true)
            return null
        }
        return transferred.value.takeIf { PipeApi.INSTANCE.getOverlappedResult(handle, pointer, transferred, false) }
    }

    override fun close() {
        Kernel32.INSTANCE.CloseHandle(event)
    }

    private companion object {
        const val ERROR_IO_PENDING = 997
        const val WAIT_OBJECT_0 = 0
    }
}
