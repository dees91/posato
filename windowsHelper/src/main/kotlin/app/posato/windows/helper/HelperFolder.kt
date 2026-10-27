package app.posato.windows.helper

import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Kernel32
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
     * Makes both folders the system's, with a protected access list for the system and administrators. A folder that
     * someone else created or that is a link is moved aside untouched rather than followed or emptied.
     */
    fun prepare(): Boolean {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !isTrusted(root)) moveAside(root)
        if (!createOrSecure(root)) return false
        if (Files.exists(helper, LinkOption.NOFOLLOW_LINKS) && !isTrusted(helper)) moveAside(helper)
        return createOrSecure(helper)
    }

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

    private fun createOrSecure(folder: Path): Boolean {
        return SecurityDescriptor(FOLDER_SDDL).use { descriptor ->
            if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
                secure(folder, descriptor)
            } else {
                Kernel32.INSTANCE.CreateDirectory(folder.toString(), descriptor.attributes())
            }
        } && isTrusted(folder)
    }

    private fun secure(
        folder: Path,
        descriptor: SecurityDescriptor,
    ): Boolean {
        val owner = PointerByReference()
        val dacl = PointerByReference()
        val present = IntByReference()
        val defaulted = IntByReference()
        val readable = Advapi32Extra.INSTANCE.getSecurityDescriptorOwner(descriptor.pointer, owner, defaulted) &&
            Advapi32Extra.INSTANCE.getSecurityDescriptorDacl(descriptor.pointer, present, dacl, defaulted)
        if (!readable) return false
        val information = WinNT.OWNER_SECURITY_INFORMATION or WinNT.DACL_SECURITY_INFORMATION or WinNT.PROTECTED_DACL_SECURITY_INFORMATION
        return Advapi32.INSTANCE.SetNamedSecurityInfo(folder.toString(), SE_FILE_OBJECT, information, owner.value, null, dacl.value, null) == 0
    }

    private fun moveAside(folder: Path) {
        val aside = folder.resolveSibling("${folder.fileName}.untrusted-${System.currentTimeMillis()}")
        Kernel32Extra.INSTANCE.moveFileExW(WString(folder.toString()), WString(aside.toString()), 0)
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
        if (result != 0) return null
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
        val TRUSTED_OWNERS = setOf(WellKnownSid.SYSTEM, WellKnownSid.ADMINISTRATORS)
    }
}
