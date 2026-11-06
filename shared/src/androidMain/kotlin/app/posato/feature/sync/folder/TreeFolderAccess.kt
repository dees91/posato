package app.posato.feature.sync.folder

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/** A folder tree the person picked, with the persistable grant still held, and the path Posato names it by. */
internal class HeldTree(
    val path: String,
    val uri: Uri,
) {
    val rootId: String = DocumentsContract.getTreeDocumentId(uri)

    /** Whether [candidate] is the tree itself or lies inside it. */
    fun contains(candidate: String): Boolean {
        return candidate == path || candidate.startsWith("$path/")
    }
}

/**
 * On Android a folder choice is a Storage Access Framework tree from the system folder picker, so Posato needs no
 * access to all files. The token is the tree's URI; the persistable read and write grant taken when it is chosen
 * keeps it usable after a restart. A tree on this phone's shared storage is named by the same
 * `/storage/emulated/0/...` path that Posato 1.6.0 stored, so the workspace binding derived from it
 * ([FolderLocation]) does not change; a tree of another provider gets a path of its own under `/documents`.
 */
internal class TreeFolderAccess(
    private val resolver: ContentResolver,
) : FolderAccess {
    @Volatile
    private var held: HeldTree? = null

    /** The tree that [resolve] last found granted, which [TreeFolderFileSystem] serves. */
    fun held(): HeldTree? {
        return held
    }

    override fun resolve(token: String): String? {
        val tree = token.treeUriOrNull()
        if (tree == null || !granted(tree)) {
            held = null
            return null
        }
        val path = pathOf(tree)
        if (held?.uri != tree) held = HeldTree(path, tree)
        return path
    }

    /** A stored path from 1.6.0 names itself, so a tree picked again can be matched to it; a tree names its mapped path. */
    override fun location(token: String): String? {
        val tree = token.treeUriOrNull() ?: return token.takeIf { it.startsWith("/") }
        return pathOf(tree)
    }

    /** The folder's name: the last part of a shared-storage path, or the name another provider gives its folder. */
    override fun display(token: String): String {
        val tree = token.treeUriOrNull() ?: return token.substringAfterLast('/')
        val path = pathOf(tree)
        if (!path.startsWith("/$OTHER_PROVIDERS/")) return path.substringAfterLast('/')
        val document = DocumentsContract.getTreeDocumentId(tree)
        return displayName(DocumentsContract.buildDocumentUriUsingTree(tree, document)) ?: document.substringAfterLast(':').substringAfterLast('/')
    }

    /**
     * Takes the persistable grant of the picked tree and gives back the one held before, so only one folder is open; a
     * folder Posato cannot create files in is refused, as a path it cannot write to is on other hosts.
     */
    override fun accept(choice: String): String? {
        val tree = choice.treeUriOrNull() ?: return null
        try {
            resolver.takePersistableUriPermission(tree, READ_WRITE)
        } catch (_: SecurityException) {
            return null
        }
        if (!createsFiles(tree)) {
            releaseQuietly(tree)
            return null
        }
        resolver.persistedUriPermissions
            .filter { it.uri != tree && DocumentsContract.isTreeUri(it.uri) }
            .forEach { previous -> releaseQuietly(previous.uri) }
        return tree.toString()
    }

    private fun granted(tree: Uri): Boolean {
        return resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }
    }

    private fun createsFiles(tree: Uri): Boolean {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS)
        return try {
            resolver.query(root, columns, null, null, null)?.use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                    cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
            } == true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun displayName(document: Uri): String? {
        return try {
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun releaseQuietly(tree: Uri) {
        try {
            resolver.releasePersistableUriPermission(tree, READ_WRITE)
        } catch (_: SecurityException) {
            return
        }
    }

    /**
     * The primary volume's tree `primary:<relative>` becomes the path 1.6.0 derived from the picker, matched exactly;
     * any other tree becomes `/documents/<authority>/<encoded document id>`.
     */
    private fun pathOf(tree: Uri): String {
        val document = DocumentsContract.getTreeDocumentId(tree)
        val volume = document.substringBefore(':')
        return if (tree.authority == EXTERNAL_STORAGE && volume == PRIMARY) {
            Environment.getExternalStorageDirectory().resolve(document.substringAfter(':', "")).absolutePath
        } else {
            "/$OTHER_PROVIDERS/${tree.authority}/${Uri.encode(document)}"
        }
    }

    private fun String.treeUriOrNull(): Uri? {
        if (!startsWith(CONTENT_SCHEME)) return null
        return Uri.parse(this).takeIf { DocumentsContract.isTreeUri(it) }
    }

    private companion object {
        const val CONTENT_SCHEME = "content://"
        const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        const val PRIMARY = "primary"
        const val OTHER_PROVIDERS = "documents"
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
