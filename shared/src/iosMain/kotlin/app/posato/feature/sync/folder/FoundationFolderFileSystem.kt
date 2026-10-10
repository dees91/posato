package app.posato.feature.sync.folder

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorWritingForDeleting
import platform.Foundation.NSFileManager
import platform.Foundation.NSFilePosixPermissions
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

/**
 * Foundation file access for the folder transport on iOS. Reads and writes go
 * through file coordination so File Provider locations (iCloud Drive,
 * Dropbox, OneDrive) download and upload them; an iCloud placeholder name is
 * reported as its real name and its download is started.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object FoundationFolderFileSystem : FolderFileSystem {
    private val manager: NSFileManager
        get() = NSFileManager.defaultManager

    override fun isDirectory(path: String): Boolean {
        return memScoped {
            val directory = alloc<BooleanVar>()
            manager.fileExistsAtPath(path, directory.ptr) && directory.value
        }
    }

    override fun isFile(path: String): Boolean {
        return memScoped {
            val directory = alloc<BooleanVar>()
            manager.fileExistsAtPath(path, directory.ptr) && !directory.value
        }
    }

    override fun readFile(
        path: String,
        maximumBytes: Int,
    ): FileRead {
        var result: FileRead = FileRead.Failed
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(NSURL.fileURLWithPath(path), 0u, null) { url ->
            val data = url?.let { NSData.dataWithContentsOfURL(it) }
            result = when {
                data == null -> if (manager.fileExistsAtPath(path)) FileRead.Failed else FileRead.Missing
                data.length.toLong() > maximumBytes -> FileRead.Failed
                else -> FileRead.Found(data.toByteArray())
            }
        }
        return result
    }

    override fun writeExclusive(
        path: String,
        bytes: ByteArray,
    ): ExclusiveWrite {
        val directory = path.parentPath()
        if (!createDirectories(directory)) return ExclusiveWrite.FAILED
        var result = ExclusiveWrite.FAILED
        NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(NSURL.fileURLWithPath(path), 0u, null) { url ->
            val target = url ?: return@coordinateWritingItemAtURL
            if (manager.fileExistsAtPath(target.path.orEmpty())) {
                result = ExclusiveWrite.EXISTS
                return@coordinateWritingItemAtURL
            }
            val temporary = NSURL.fileURLWithPath(directory.child(temporaryName()))
            if (!bytes.toData().writeToURL(temporary, atomically = false)) return@coordinateWritingItemAtURL
            result = if (manager.moveItemAtURL(temporary, target, null)) {
                ExclusiveWrite.CREATED
            } else {
                manager.removeItemAtURL(temporary, null)
                if (manager.fileExistsAtPath(target.path.orEmpty())) ExclusiveWrite.EXISTS else ExclusiveWrite.FAILED
            }
        }
        return result
    }

    override fun replace(
        path: String,
        bytes: ByteArray,
    ): Boolean {
        if (!createDirectories(path.parentPath())) return false
        var written = false
        NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(NSURL.fileURLWithPath(path), 0u, null) { url ->
            written = url != null && bytes.toData().writeToURL(url, atomically = true)
        }
        return written
    }

    override fun names(directory: String): List<String>? {
        if (!isDirectory(directory)) return emptyList()
        val entries = manager.contentsOfDirectoryAtPath(directory, null) ?: return null
        return entries.filterIsInstance<String>().map { name ->
            if (name.startsWith(".") && name.endsWith(".icloud")) {
                manager.startDownloadingUbiquitousItemAtURL(NSURL.fileURLWithPath(directory.child(name)), null)
                name.removePrefix(".").removeSuffix(".icloud")
            } else {
                name
            }
        }
    }

    override fun delete(path: String): Boolean {
        if (!manager.fileExistsAtPath(path)) return true
        var deleted = false
        NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(
            NSURL.fileURLWithPath(path),
            NSFileCoordinatorWritingForDeleting,
            null,
        ) { url ->
            deleted = url != null && manager.removeItemAtURL(url, null)
        }
        return deleted || !manager.fileExistsAtPath(path)
    }

    override fun deleteTree(path: String): Boolean {
        return delete(path)
    }

    override fun createPrivateDirectories(path: String): Boolean {
        if (isDirectory(path)) return true
        val attributes = mapOf<Any?, Any?>(
            NSFilePosixPermissions to NSNumber(int = PRIVATE_DIRECTORY_MODE),
            NSFileProtectionKey to NSFileProtectionComplete,
        )
        return manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = attributes, error = null)
    }

    override fun createDirectories(path: String): Boolean {
        if (isDirectory(path)) return true
        return manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    }

    override fun canonical(path: String): String? {
        if (!manager.fileExistsAtPath(path)) return null
        return NSURL.fileURLWithPath(path).URLByResolvingSymlinksInPath?.path
    }

    override fun isWritableDirectory(path: String): Boolean {
        return isDirectory(path) && manager.isWritableFileAtPath(path)
    }

    override fun restrictToOwner(path: String) {
        val attributes = mapOf<Any?, Any?>(
            NSFilePosixPermissions to NSNumber(int = PRIVATE_FILE_MODE),
            NSFileProtectionKey to NSFileProtectionComplete,
        )
        manager.setAttributes(attributes, ofItemAtPath = path, error = null)
    }
}

private const val PRIVATE_DIRECTORY_MODE: Int = 0x1C0
private const val PRIVATE_FILE_MODE: Int = 0x180

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return result
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
