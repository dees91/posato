package app.posato.feature.sync.folder

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create

/** Implemented in Swift: shows the Files folder picker and returns a base64 bookmark to the chosen folder, or null. */
interface IosFolderPicker {
    fun pickFolder(completion: (String?) -> Unit)
}

/**
 * On iOS a folder choice is a security-scoped bookmark: resolving it starts
 * access to the folder for the life of the process, and a bookmark that has
 * gone stale is renewed in place.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class BookmarkFolderAccess : FolderAccess {
    private var accessing: NSURL? = null
    private var accessingToken: String? = null

    override fun resolve(token: String): String? {
        if (token == accessingToken) return accessing?.path
        val url = resolveBookmark(token) ?: return null
        accessing?.stopAccessingSecurityScopedResource()
        url.startAccessingSecurityScopedResource()
        accessing = url
        accessingToken = token
        return url.path
    }

    override fun display(token: String): String {
        return resolveBookmark(token)?.lastPathComponent.orEmpty()
    }

    override fun accept(choice: String): String? {
        return choice.takeIf { resolveBookmark(it) != null }
    }

    private fun resolveBookmark(token: String): NSURL? {
        val data = NSData.create(base64EncodedString = token, options = 0u) ?: return null
        return memScoped {
            val stale = alloc<BooleanVar>()
            NSURL.URLByResolvingBookmarkData(data, options = 0u, relativeToURL = null, bookmarkDataIsStale = stale.ptr, error = null)
        }
    }
}
