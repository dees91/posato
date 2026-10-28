package app.posato.windows.helper

import com.sun.jna.Memory
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Path

/** What [FolderSecurity.secureExisting] found and did. */
internal enum class ExistingFolder { SECURED, UNTRUSTED, FAILED }

/** Owners and access lists of the service's folders, read and set without following a link. */
internal object FolderSecurity {
    /** The owner's SID of [path] as text, or null when it cannot be read. */
    fun owner(path: Path): String? {
        val owner = PointerByReference()
        val descriptor = PointerByReference()
        val result = Advapi32.INSTANCE.GetNamedSecurityInfo(
            path.toString(),
            SE_FILE_OBJECT,
            WinNT.OWNER_SECURITY_INFORMATION,
            owner,
            null,
            null,
            null,
            descriptor,
        )
        return if (result == 0) sidText(owner, descriptor) else null
    }

    /**
     * Opens [folder] itself, not what a link points to, and, when it is a plain folder of one of [trustedOwners], gives it
     * the owner and protected access list of [sddl] through that same handle.
     */
    fun secureExisting(
        folder: Path,
        sddl: String,
        trustedOwners: Set<String>,
    ): ExistingFolder {
        val handle = Kernel32.INSTANCE.CreateFile(
            folder.toString(),
            READ_CONTROL or WRITE_DAC or WRITE_OWNER or FILE_READ_ATTRIBUTES,
            WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE or WinNT.FILE_SHARE_DELETE,
            null,
            WinNT.OPEN_EXISTING,
            WinNT.FILE_FLAG_BACKUP_SEMANTICS or WinNT.FILE_FLAG_OPEN_REPARSE_POINT,
            null,
        )
        if (handle == WinBase.INVALID_HANDLE_VALUE) return ExistingFolder.UNTRUSTED
        try {
            val plainFolder = attributes(handle)?.let { it and Win32.FILE_ATTRIBUTE_REPARSE_POINT == 0 && it and FILE_ATTRIBUTE_DIRECTORY != 0 }
            if (plainFolder != true || owner(handle) !in trustedOwners) return ExistingFolder.UNTRUSTED
            return SecurityDescriptor(sddl).use { descriptor -> if (secure(handle, descriptor)) ExistingFolder.SECURED else ExistingFolder.FAILED }
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }

    /** The file attributes read through [handle] (`FILE_BASIC_INFO`). */
    private fun attributes(handle: WinNT.HANDLE): Int? {
        val info = Memory(FILE_BASIC_INFO_SIZE)
        val read = Kernel32.INSTANCE.GetFileInformationByHandleEx(handle, FILE_BASIC_INFO_CLASS, info, WinDef.DWORD(FILE_BASIC_INFO_SIZE))
        return if (read) info.getInt(FILE_BASIC_INFO_ATTRIBUTES) else null
    }

    private fun secure(
        handle: WinNT.HANDLE,
        descriptor: SecurityDescriptor,
    ): Boolean {
        val owner = PointerByReference()
        val dacl = PointerByReference()
        val present = IntByReference()
        val defaulted = IntByReference()
        val readable = Advapi32Extra.INSTANCE.getSecurityDescriptorOwner(descriptor.address, owner, defaulted) &&
            Advapi32Extra.INSTANCE.getSecurityDescriptorDacl(descriptor.address, present, dacl, defaulted)
        if (!readable) return false
        val information = WinNT.OWNER_SECURITY_INFORMATION or WinNT.DACL_SECURITY_INFORMATION or WinNT.PROTECTED_DACL_SECURITY_INFORMATION
        return Advapi32.INSTANCE.SetSecurityInfo(handle, SE_FILE_OBJECT, information, owner.value, null, dacl.value, null) == 0
    }

    private fun owner(handle: WinNT.HANDLE): String? {
        val owner = PointerByReference()
        val descriptor = PointerByReference()
        val result = Advapi32.INSTANCE.GetSecurityInfo(handle, SE_FILE_OBJECT, WinNT.OWNER_SECURITY_INFORMATION, owner, null, null, null, descriptor)
        return if (result == 0) sidText(owner, descriptor) else null
    }

    /** The owner's SID as text; frees the security descriptor that holds it. */
    private fun sidText(
        owner: PointerByReference,
        descriptor: PointerByReference,
    ): String? {
        return try {
            val text = PointerByReference()
            if (Advapi32.INSTANCE.ConvertSidToStringSid(WinNT.PSID(owner.value), text)) {
                text.value.getWideString(0).also { Kernel32.INSTANCE.LocalFree(text.value) }
            } else {
                null
            }
        } finally {
            Kernel32.INSTANCE.LocalFree(descriptor.value)
        }
    }

    private const val SE_FILE_OBJECT = 1
    private const val READ_CONTROL = 0x00020000
    private const val WRITE_DAC = 0x00040000
    private const val WRITE_OWNER = 0x00080000
    private const val FILE_READ_ATTRIBUTES = 0x0080
    private const val FILE_ATTRIBUTE_DIRECTORY = 0x10
    private const val FILE_BASIC_INFO_CLASS = 0
    private const val FILE_BASIC_INFO_SIZE = 40L
    private const val FILE_BASIC_INFO_ATTRIBUTES = 32L
}
