package app.posato.windows.helper

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.Winsvc
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.charset.StandardCharsets

/**
 * The Windows functions that `jna-platform` lacks or binds with Java arrays, which overlapped I/O cannot use. Kotlin
 * names start in lower case; the mapper restores the exported name. Nothing here loads before a Windows code path
 * calls it.
 */
internal interface Kernel32Extra : StdCallLibrary {
    fun createNamedPipeW(
        name: WString,
        openMode: Int,
        pipeMode: Int,
        maxInstances: Int,
        outBufferSize: Int,
        inBufferSize: Int,
        defaultTimeout: Int,
        attributes: WinBase.SECURITY_ATTRIBUTES?,
    ): HANDLE

    fun connectNamedPipe(
        pipe: HANDLE,
        overlapped: Pointer,
    ): Boolean

    fun readFile(
        file: HANDLE,
        buffer: Pointer,
        toRead: Int,
        read: IntByReference?,
        overlapped: Pointer,
    ): Boolean

    fun writeFile(
        file: HANDLE,
        buffer: Pointer,
        toWrite: Int,
        written: IntByReference?,
        overlapped: Pointer,
    ): Boolean

    fun getOverlappedResult(
        file: HANDLE,
        overlapped: Pointer,
        transferred: IntByReference,
        wait: Boolean,
    ): Boolean

    fun cancelIoEx(
        file: HANDLE,
        overlapped: Pointer?,
    ): Boolean

    fun moveFileExW(
        existing: WString,
        replacement: WString,
        flags: Int,
    ): Boolean

    fun getFileAttributesW(name: WString): Int

    companion object {
        val INSTANCE: Kernel32Extra by lazy { load("kernel32") }
    }
}

internal interface Advapi32Extra : StdCallLibrary {
    fun convertStringSecurityDescriptorToSecurityDescriptorW(
        text: WString,
        revision: Int,
        descriptor: PointerByReference,
        size: IntByReference?,
    ): Boolean

    fun impersonateNamedPipeClient(pipe: HANDLE): Boolean

    fun getSecurityDescriptorOwner(
        descriptor: Pointer,
        owner: PointerByReference,
        defaulted: IntByReference,
    ): Boolean

    fun getSecurityDescriptorDacl(
        descriptor: Pointer,
        present: IntByReference,
        dacl: PointerByReference,
        defaulted: IntByReference,
    ): Boolean

    fun changeServiceConfigW(
        service: Winsvc.SC_HANDLE,
        serviceType: Int,
        startType: Int,
        errorControl: Int,
        binaryPath: WString?,
        loadOrderGroup: WString?,
        tagId: Pointer?,
        dependencies: WString?,
        account: WString?,
        password: WString?,
        displayName: WString?,
    ): Boolean

    fun changeServiceConfig2W(
        service: Winsvc.SC_HANDLE,
        level: Int,
        info: Pointer,
    ): Boolean

    companion object {
        val INSTANCE: Advapi32Extra by lazy { load("advapi32") }
    }
}

private inline fun <reified T : Library> load(name: String): T {
    val mapper = FunctionMapper { _, method -> method.name.replaceFirstChar(Char::uppercaseChar) }
    return Native.load(name, T::class.java, mapOf(Library.OPTION_FUNCTION_MAPPER to mapper))
}

/** A self-relative security descriptor from SDDL; [close] frees it. */
internal class SecurityDescriptor(
    sddl: String,
) : AutoCloseable {
    val address: Pointer
    val size: Int

    init {
        val descriptor = PointerByReference()
        val length = IntByReference()
        check(Advapi32Extra.INSTANCE.convertStringSecurityDescriptorToSecurityDescriptorW(WString(sddl), SDDL_REVISION_1, descriptor, length)) {
            "security descriptor ${Native.getLastError()}"
        }
        address = descriptor.value
        size = length.value
    }

    fun attributes(): WinBase.SECURITY_ATTRIBUTES {
        return WinBase.SECURITY_ATTRIBUTES().apply {
            lpSecurityDescriptor = address
            bInheritHandle = false
        }
    }

    override fun close() {
        Kernel32.INSTANCE.LocalFree(address)
    }

    private companion object {
        const val SDDL_REVISION_1 = 1
    }
}

/** A Windows multi-string: each value ends with a NUL, and one more NUL ends the list. */
internal fun multiString(values: List<String>): Memory {
    val bytes = (values.joinToString("") { "$it\u0000" } + "\u0000").toByteArray(StandardCharsets.UTF_16LE)
    return Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
}

internal object Win32 {
    const val ERROR_IO_PENDING = 997
    const val ERROR_PIPE_CONNECTED = 535
    const val ERROR_SERVICE_EXISTS = 1073
    const val ERROR_SERVICE_DOES_NOT_EXIST = 1060
    const val ERROR_SERVICE_ALREADY_RUNNING = 1056
    const val ERROR_SERVICE_NOT_ACTIVE = 1062
    const val ERROR_FAILED_SERVICE_CONTROLLER_CONNECT = 1063
    const val WAIT_OBJECT_0 = 0
    const val INFINITE = -1
    const val INVALID_FILE_ATTRIBUTES = -1
    const val FILE_ATTRIBUTE_REPARSE_POINT = 0x400
}
