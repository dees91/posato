package app.posato.windows.helper

import com.sun.jna.Memory
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * `%ProgramData%\Posato\helper`, where the service keeps its configuration and state. Only the system and
 * administrators can open it; before every write the service checks that the folders are still theirs and are not
 * links elsewhere, and a file is replaced in one NTFS rename.
 */
internal class HelperFolder(
    val root: Path = Paths.get(System.getenv("ProgramData") ?: DEFAULT_PROGRAM_DATA, "Posato"),
) {
    val helper: Path = root.resolve("helper")
    val config: Path = helper.resolve("config")
    val state: Path = helper.resolve("state")

    /** Whether [path] exists, is no reparse point, and belongs to the system or the administrators. */
    fun isTrusted(path: Path): Boolean {
        val attributes = Kernel32Extra.INSTANCE.getFileAttributesW(WString(path.toString()))
        if (attributes == Win32.INVALID_FILE_ATTRIBUTES || attributes and Win32.FILE_ATTRIBUTE_REPARSE_POINT != 0) return false
        return owner(path) in TRUSTED_OWNERS
    }

    fun read(file: Path): String? {
        if (!isTrusted(root) || !isTrusted(helper) || !isTrusted(file)) return null
        return try {
            Files.readString(file)
        } catch (_: IOException) {
            null
        }
    }

    /** Writes [text] beside [target] and renames it over in one step; false when the folders are not trusted. */
    fun write(
        target: Path,
        text: String,
    ): Boolean {
        if (!isTrusted(root) || !isTrusted(helper)) return false
        val temporary = helper.resolve(".${UUID.randomUUID()}.tmp")
        return try {
            FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                channel.write(ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8)))
                channel.force(true)
            }
            Kernel32Extra.INSTANCE.moveFileExW(WString(temporary.toString()), WString(target.toString()), MOVEFILE_REPLACE_AND_FLUSH)
        } catch (_: IOException) {
            false
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }
    }

    fun delete(file: Path) {
        if (isTrusted(root) && isTrusted(helper)) runCatching { Files.deleteIfExists(file) }
    }

    /**
     * Makes both folders the system's, with a protected access list for the system and administrators. An existing
     * folder is checked and secured through one handle opened on the folder itself, so a link is never followed. A
     * folder that someone else owns, or a link, is moved aside untouched; when it cannot be moved, or the path is back
     * before the new folder is created with its access list, nothing is set up.
     */
    fun prepare(): Boolean = establish(root) && establish(helper)

    /** Deletes both folders when they are still trusted; anything moved aside earlier stays as it was found. */
    fun purge() {
        if (!isTrusted(root)) return
        listOf(state, config).forEach { runCatching { Files.deleteIfExists(it) } }
        runCatching {
            Files.list(helper).use { files -> files.filter { it.fileName.toString().endsWith(".tmp") }.forEach(Files::deleteIfExists) }
        }
        runCatching { Files.deleteIfExists(helper) }
        runCatching { Files.deleteIfExists(root) }
    }

    private fun establish(folder: Path): Boolean {
        if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
            when (secureExisting(folder)) {
                Existing.SECURED -> return isTrusted(folder)
                Existing.FAILED -> return false
                Existing.UNTRUSTED -> if (!moveAside(folder) || Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return false
            }
        }
        // CreateDirectory fails when anything has taken the path meanwhile.
        return SecurityDescriptor(FOLDER_SDDL).use { Kernel32.INSTANCE.CreateDirectory(folder.toString(), it.attributes()) } &&
            isTrusted(folder)
    }

    private enum class Existing { SECURED, UNTRUSTED, FAILED }

    /** Opens [folder] itself, not what a link points to, and secures it when it is a plain folder of a trusted owner. */
    private fun secureExisting(folder: Path): Existing {
        val handle = Kernel32.INSTANCE.CreateFile(
            folder.toString(),
            READ_CONTROL or WRITE_DAC or WRITE_OWNER or FILE_READ_ATTRIBUTES,
            WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE or WinNT.FILE_SHARE_DELETE,
            null,
            WinNT.OPEN_EXISTING,
            WinNT.FILE_FLAG_BACKUP_SEMANTICS or WinNT.FILE_FLAG_OPEN_REPARSE_POINT,
            null,
        )
        if (handle == WinBase.INVALID_HANDLE_VALUE) return Existing.UNTRUSTED
        try {
            val plainFolder = attributes(handle)?.let { it and Win32.FILE_ATTRIBUTE_REPARSE_POINT == 0 && it and FILE_ATTRIBUTE_DIRECTORY != 0 }
            if (plainFolder != true || owner(handle) !in TRUSTED_OWNERS) return Existing.UNTRUSTED
            return SecurityDescriptor(FOLDER_SDDL).use { descriptor -> if (secure(handle, descriptor)) Existing.SECURED else Existing.FAILED }
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

    private fun moveAside(folder: Path): Boolean {
        val aside = folder.resolveSibling("${folder.fileName}.untrusted-${System.currentTimeMillis()}")
        return Kernel32Extra.INSTANCE.moveFileExW(WString(folder.toString()), WString(aside.toString()), 0)
    }

    private fun owner(handle: WinNT.HANDLE): String? {
        val owner = PointerByReference()
        val descriptor = PointerByReference()
        val result = Advapi32.INSTANCE.GetSecurityInfo(handle, SE_FILE_OBJECT, WinNT.OWNER_SECURITY_INFORMATION, owner, null, null, null, descriptor)
        return if (result == 0) sidText(owner, descriptor) else null
    }

    private fun owner(path: Path): String? {
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

    private companion object {
        const val DEFAULT_PROGRAM_DATA = "C:\\ProgramData"
        const val SE_FILE_OBJECT = 1
        const val MOVEFILE_REPLACE_AND_FLUSH = 0x1 or 0x8
        const val FOLDER_SDDL = "O:SYG:SYD:P(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)"
        const val READ_CONTROL = 0x00020000
        const val WRITE_DAC = 0x00040000
        const val WRITE_OWNER = 0x00080000
        const val FILE_READ_ATTRIBUTES = 0x0080
        const val FILE_ATTRIBUTE_DIRECTORY = 0x10
        const val FILE_BASIC_INFO_CLASS = 0
        const val FILE_BASIC_INFO_SIZE = 40L
        const val FILE_BASIC_INFO_ATTRIBUTES = 32L
        val TRUSTED_OWNERS = setOf(WellKnownSid.SYSTEM, WellKnownSid.ADMINISTRATORS)
    }
}
