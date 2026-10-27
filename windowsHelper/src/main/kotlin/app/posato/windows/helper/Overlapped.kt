package app.posato.windows.helper

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.IntByReference

/**
 * One overlapped operation on [handle] that gives up after a deadline, so a client that never sends cannot hold a
 * worker: the operation is cancelled and awaited before its buffer and structure are released.
 */
internal class Overlapped(
    private val handle: HANDLE,
) : AutoCloseable {
    private val event: HANDLE = Kernel32.INSTANCE.CreateEvent(null, true, false, null)
        ?: throw IllegalStateException("event ${Native.getLastError()}")
    private val overlapped = WinBase.OVERLAPPED()

    /** Runs [start] and returns the bytes transferred, or null when it failed or [timeoutMillis] passed. */
    fun run(
        timeoutMillis: Int,
        start: (Pointer) -> Boolean,
    ): Int? {
        Kernel32.INSTANCE.ResetEvent(event)
        overlapped.clear()
        overlapped.hEvent = event
        overlapped.write()
        val pointer = overlapped.pointer
        val started = start(pointer)
        val error = if (started) 0 else Native.getLastError()
        return when {
            error == Win32.ERROR_PIPE_CONNECTED -> 0
            started || error == Win32.ERROR_IO_PENDING -> await(pointer, timeoutMillis)
            else -> null
        }
    }

    private fun await(
        pointer: Pointer,
        timeoutMillis: Int,
    ): Int? {
        val transferred = IntByReference()
        if (Kernel32.INSTANCE.WaitForSingleObject(event, timeoutMillis) != Win32.WAIT_OBJECT_0) {
            Kernel32Extra.INSTANCE.cancelIoEx(handle, pointer)
            Kernel32Extra.INSTANCE.getOverlappedResult(handle, pointer, transferred, true)
            return null
        }
        return transferred.value.takeIf { Kernel32Extra.INSTANCE.getOverlappedResult(handle, pointer, transferred, false) }
    }

    override fun close() {
        Kernel32.INSTANCE.CloseHandle(event)
    }
}
