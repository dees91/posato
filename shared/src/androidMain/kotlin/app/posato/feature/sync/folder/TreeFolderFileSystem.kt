package app.posato.feature.sync.folder

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * The folder transport's file operations on the picked tree, through `DocumentsContract`. A path under the tree is
 * found by its names from the tree's root down ([TreeDocuments]). Everything fails closed while no tree is held, so a
 * lost grant reads as a failure, never as an empty folder.
 */
internal class TreeFolderFileSystem private constructor(
    documents: TreeDocuments,
) : FolderFileSystem,
    FolderReads by TreeReads(documents),
    FolderWrites by TreeWrites(documents) {
    constructor(resolver: ContentResolver, held: () -> HeldTree?) : this(TreeDocuments(resolver, held))
}

internal class TreeDocument(
    val id: String,
    val directory: Boolean,
)

internal sealed interface TreeLookup {
    class Found(
        val document: TreeDocument,
    ) : TreeLookup

    data object Absent : TreeLookup

    data object Failed : TreeLookup
}

/**
 * Finds documents under the held tree by name. The document ids of each directory's children are kept from its last
 * listing and listed again when a name is missing from it; a failed listing, a write, or a kept id that no longer
 * opens drops every kept listing.
 */
internal class TreeDocuments(
    val resolver: ContentResolver,
    val held: () -> HeldTree?,
) {
    private val lock = Any()
    private var listedTree: Uri? = null
    private val listings = HashMap<String, Map<String, TreeDocument>>()

    fun lookup(path: String): TreeLookup {
        val tree = held()?.takeIf { it.contains(path) } ?: return TreeLookup.Failed
        var parentPath = tree.path
        var current = TreeDocument(tree.rootId, directory = true)
        for (name in tree.names(path)) {
            if (!current.directory) return TreeLookup.Absent
            current = when (val found = child(tree, parentPath, current.id, name)) {
                is TreeLookup.Found -> found.document
                else -> return found
            }
            parentPath = parentPath.child(name)
        }
        return TreeLookup.Found(current)
    }

    /** A child from the directory's kept listing; a name missing from it lists the directory again. */
    fun child(
        tree: HeldTree,
        parentPath: String,
        parentId: String,
        name: String,
    ): TreeLookup {
        val kept = synchronized(lock) { listings[parentPath].takeIf { listedTree == tree.uri } }
        kept?.get(name)?.let { return TreeLookup.Found(it) }
        val listed = list(tree, parentPath, parentId) ?: return TreeLookup.Failed
        return listed[name]?.let(TreeLookup::Found) ?: TreeLookup.Absent
    }

    fun list(
        tree: HeldTree,
        parentPath: String,
        parentId: String,
    ): Map<String, TreeDocument>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree.uri, parentId)
        val listed = providerCall {
            resolver.query(children, CHILD_COLUMNS, null, null, null)?.use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(NAME_COLUMN) ?: continue
                        put(name, TreeDocument(cursor.getString(ID_COLUMN), cursor.getString(MIME_COLUMN) == Document.MIME_TYPE_DIR))
                    }
                }
            }
        }
        synchronized(lock) {
            if (listedTree != tree.uri) {
                listings.clear()
                listedTree = tree.uri
            }
            if (listed == null) listings.clear() else listings[parentPath] = listed
        }
        return listed
    }

    fun forget() {
        synchronized(lock) { listings.clear() }
    }

    fun uri(
        tree: HeldTree,
        documentId: String,
    ): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(tree.uri, documentId)
    }

    /** Whether the document is a directory and accepts new children, or null when it cannot be read. */
    fun directoryFlags(
        tree: HeldTree,
        documentId: String,
    ): Pair<Boolean, Boolean>? {
        return providerCall {
            resolver.query(uri(tree, documentId), FLAG_COLUMNS, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                (cursor.getString(0) == Document.MIME_TYPE_DIR) to (cursor.getInt(1) and Document.FLAG_DIR_SUPPORTS_CREATE != 0)
            }
        }
    }

    fun displayName(document: Uri): String? {
        return providerCall {
            resolver.query(document, NAME_COLUMNS, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }
    }

    fun delete(document: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(resolver, document)
        } catch (_: FileNotFoundException) {
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    private companion object {
        const val ID_COLUMN = 0
        const val NAME_COLUMN = 1
        const val MIME_COLUMN = 2
        val CHILD_COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val FLAG_COLUMNS = arrayOf(Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS)
        val NAME_COLUMNS = arrayOf(Document.COLUMN_DISPLAY_NAME)
    }
}

/** The names from the tree's root down to [path], which lies in the tree. */
internal fun HeldTree.names(path: String): List<String> {
    return path.removePrefix(this.path).split('/').filter(String::isNotEmpty)
}

/** A provider call; its failures (a gone document, a lost grant, a provider that refuses) come back as null. */
internal fun <T> providerCall(call: () -> T?): T? {
    return try {
        call()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }
}

private class TreeReads(
    private val documents: TreeDocuments,
) : FolderReads {
    override fun isDirectory(path: String): Boolean {
        return (documents.lookup(path) as? TreeLookup.Found)?.document?.directory == true
    }

    override fun isFile(path: String): Boolean {
        return (documents.lookup(path) as? TreeLookup.Found)?.document?.directory == false
    }

    /** A kept id that no longer opens means the file changed under it, so the path is looked up once more from fresh listings. */
    override fun readFile(
        path: String,
        maximumBytes: Int,
    ): FileRead {
        val first = readOnce(path, maximumBytes)
        if (first != null) return first
        documents.forget()
        return readOnce(path, maximumBytes) ?: FileRead.Missing
    }

    override fun names(directory: String): List<String>? {
        val tree = documents.held() ?: return null
        return when (val found = documents.lookup(directory)) {
            TreeLookup.Absent -> emptyList()
            TreeLookup.Failed -> null
            is TreeLookup.Found -> if (found.document.directory) documents.list(tree, directory, found.document.id)?.keys?.toList() else emptyList()
        }
    }

    /** The path itself while its tree is held and its document exists; the tree's root is asked for directly. */
    override fun canonical(path: String): String? {
        val tree = documents.held()?.takeIf { it.contains(path) } ?: return null
        if (path == tree.path) return path.takeIf { documents.directoryFlags(tree, tree.rootId)?.first == true }
        return path.takeIf { documents.lookup(path) is TreeLookup.Found }
    }

    override fun isWritableDirectory(path: String): Boolean {
        val tree = documents.held() ?: return false
        val found = documents.lookup(path) as? TreeLookup.Found ?: return false
        return documents.directoryFlags(tree, found.document.id) == (true to true)
    }

    /** One read, or null when the kept document id no longer opens. */
    private fun readOnce(
        path: String,
        maximumBytes: Int,
    ): FileRead? {
        val tree = documents.held() ?: return FileRead.Failed
        val document = when (val found = documents.lookup(path)) {
            TreeLookup.Absent -> return FileRead.Missing
            TreeLookup.Failed -> return FileRead.Failed
            is TreeLookup.Found -> found.document
        }
        if (document.directory) return FileRead.Missing
        return try {
            documents.resolver.openInputStream(documents.uri(tree, document.id))?.use { readAtMost(it, maximumBytes) } ?: FileRead.Failed
        } catch (_: FileNotFoundException) {
            null
        } catch (_: IOException) {
            FileRead.Failed
        } catch (_: SecurityException) {
            FileRead.Failed
        } catch (_: IllegalArgumentException) {
            FileRead.Failed
        }
    }
}

private class TreeWrites(
    private val documents: TreeDocuments,
) : FolderWrites {
    /**
     * Writes a complete temporary sibling and renames it to [path]'s name, so no reader sees a partial file. A provider
     * that finds the name taken renames to another one, such as `name (1)`; that copy is deleted and the name is taken.
     */
    override fun writeExclusive(
        path: String,
        bytes: ByteArray,
    ): ExclusiveWrite {
        val tree = documents.held() ?: return ExclusiveWrite.FAILED
        val parentPath = path.parentPath()
        if (!tree.contains(parentPath) || !createDirectories(parentPath)) return ExclusiveWrite.FAILED
        val parent = (documents.lookup(parentPath) as? TreeLookup.Found)?.document ?: return ExclusiveWrite.FAILED
        val siblings = documents.list(tree, parentPath, parent.id) ?: return ExclusiveWrite.FAILED
        val name = path.substringAfterLast('/')
        if (name in siblings) return ExclusiveWrite.EXISTS
        val temporary = providerCall {
            DocumentsContract.createDocument(documents.resolver, documents.uri(tree, parent.id), BINARY, temporaryName())
        } ?: return ExclusiveWrite.FAILED
        val result = placeInto(temporary, bytes, name)
        documents.forget()
        return result
    }

    private fun placeInto(
        temporary: Uri,
        bytes: ByteArray,
        name: String,
    ): ExclusiveWrite {
        val written = providerCall { documents.resolver.openOutputStream(temporary, WRITE)?.use { it.write(bytes) } } != null
        if (!written) return ExclusiveWrite.FAILED.also { documents.delete(temporary) }
        // A provider that keeps the document id answers no new URI, and so does a rename that failed: the name tells.
        val placed = providerCall { DocumentsContract.renameDocument(documents.resolver, temporary, name) } ?: temporary
        val placedName = documents.displayName(placed)
        if (placedName == name) return ExclusiveWrite.CREATED
        documents.delete(placed)
        // Still the temporary name: the rename failed. Any other name: the provider found the name taken.
        return if (placedName == null || placedName.startsWith(TEMPORARY_PREFIX)) ExclusiveWrite.FAILED else ExclusiveWrite.EXISTS
    }

    /** Only Posato's own files, in its private directory, are replaced; the shared folder is written once per name. */
    override fun replace(
        path: String,
        bytes: ByteArray,
    ): Boolean {
        return false
    }

    override fun delete(path: String): Boolean {
        val tree = documents.held() ?: return false
        return when (val found = documents.lookup(path)) {
            TreeLookup.Absent -> true
            TreeLookup.Failed -> false
            is TreeLookup.Found -> documents.delete(documents.uri(tree, found.document.id)).also { documents.forget() }
        }
    }

    /** A provider deletes a directory with everything in it. */
    override fun deleteTree(path: String): Boolean {
        return delete(path)
    }

    override fun createPrivateDirectories(path: String): Boolean {
        return createDirectories(path)
    }

    /** Creates each missing directory from the tree's root down; a name taken by a file, or renamed on a clash, fails. */
    override fun createDirectories(path: String): Boolean {
        val tree = documents.held()?.takeIf { it.contains(path) } ?: return false
        var parentPath = tree.path
        var parent: TreeDocument? = TreeDocument(tree.rootId, directory = true)
        for (name in tree.names(path)) {
            parent = parent?.let { directory(tree, parentPath, it, name) }
            parentPath = parentPath.child(name)
        }
        return parent != null
    }

    /** The shared folder keeps the access its provider gives; only Posato's private files are restricted. */
    override fun restrictToOwner(path: String) {
        return
    }

    /**
     * The directory [name] in [parent], created when it is missing, or null when a file holds the name or it fails. A
     * kept file under the name is checked against a fresh listing first, since another device may have replaced it.
     */
    private fun directory(
        tree: HeldTree,
        parentPath: String,
        parent: TreeDocument,
        name: String,
    ): TreeDocument? {
        val kept = documents.child(tree, parentPath, parent.id, name)
        val found = if (kept is TreeLookup.Found && !kept.document.directory) fresh(tree, parentPath, parent, name) else kept
        return when (found) {
            TreeLookup.Failed -> null
            is TreeLookup.Found -> found.document.takeIf { it.directory }
            TreeLookup.Absent -> createDirectory(tree, parent.id, name)
        }
    }

    private fun fresh(
        tree: HeldTree,
        parentPath: String,
        parent: TreeDocument,
        name: String,
    ): TreeLookup {
        val listed = documents.list(tree, parentPath, parent.id) ?: return TreeLookup.Failed
        return listed[name]?.let(TreeLookup::Found) ?: TreeLookup.Absent
    }

    private fun createDirectory(
        tree: HeldTree,
        parentId: String,
        name: String,
    ): TreeDocument? {
        val created = providerCall {
            DocumentsContract.createDocument(documents.resolver, documents.uri(tree, parentId), Document.MIME_TYPE_DIR, name)
        }
        documents.forget()
        if (created == null) return null
        if (documents.displayName(created) != name) return null.also { documents.delete(created) }
        return TreeDocument(DocumentsContract.getDocumentId(created), directory = true)
    }

    private companion object {
        const val BINARY = "application/octet-stream"
        const val WRITE = "w"
    }
}

/** Reads the whole stream, or fails once it holds more than [maximumBytes]. */
private fun readAtMost(
    input: InputStream,
    maximumBytes: Int,
): FileRead {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        if (output.size() > maximumBytes) return FileRead.Failed
    }
    return FileRead.Found(output.toByteArray())
}

private const val BUFFER_BYTES = 8_192
