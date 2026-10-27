package app.posato.windows.helper

import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference

/**
 * Ends the installing user's processes that run a chosen program. The image and the owner are read through the same
 * handle that terminates the process, so a process identifier reused meanwhile is never ended by mistake.
 */
internal class ProcessEnder(
    private val userSid: String,
) {
    fun endMatching(executables: List<String>) {
        if (executables.isEmpty()) return
        ProcessHandle.allProcesses().forEach { process ->
            val command = process.info().command().orElse(null) ?: return@forEach
            if (executables.none { it.equals(command, ignoreCase = true) }) return@forEach
            endIfMatching(process.pid().toInt(), executables)
        }
    }

    private fun endIfMatching(
        pid: Int,
        executables: List<String>,
    ) {
        val process = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION or PROCESS_TERMINATE, false, pid) ?: return
        try {
            val image = image(process) ?: return
            val owner = owner(process) ?: return
            if (ProcessMatch.shouldEnd(image, owner, userSid, executables)) Kernel32.INSTANCE.TerminateProcess(process, EXIT_CODE)
        } finally {
            Kernel32.INSTANCE.CloseHandle(process)
        }
    }

    private fun image(process: WinNT.HANDLE): String? {
        val buffer = CharArray(MAX_IMAGE_PATH)
        val length = IntByReference(buffer.size)
        if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, length)) return null
        return String(buffer, 0, length.value)
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

    private companion object {
        const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
        const val PROCESS_TERMINATE = 0x0001
        const val MAX_IMAGE_PATH = 32_768
        const val EXIT_CODE = 1
    }
}
