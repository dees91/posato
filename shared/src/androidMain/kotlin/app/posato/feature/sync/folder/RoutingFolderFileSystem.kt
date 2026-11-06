package app.posato.feature.sync.folder

/**
 * Sends paths in Posato's private directory to `java.nio` and every other path to the picked tree, which fails while
 * no tree is held, so a folder path never falls back to direct file access.
 */
internal class RoutingFolderFileSystem private constructor(
    route: FolderRoute,
) : FolderFileSystem,
    FolderReads by RoutedReads(route),
    FolderWrites by RoutedWrites(route) {
    constructor(privateDirectory: String, tree: FolderFileSystem, local: FolderFileSystem) : this(FolderRoute(privateDirectory, tree, local))
}

private class FolderRoute(
    private val privateDirectory: String,
    private val tree: FolderFileSystem,
    private val local: FolderFileSystem,
) {
    fun on(path: String): FolderFileSystem {
        return if (path == privateDirectory || path.startsWith("$privateDirectory/")) local else tree
    }
}

private class RoutedReads(
    private val route: FolderRoute,
) : FolderReads {
    override fun isDirectory(path: String): Boolean {
        return route.on(path).isDirectory(path)
    }

    override fun isFile(path: String): Boolean {
        return route.on(path).isFile(path)
    }

    override fun readFile(
        path: String,
        maximumBytes: Int,
    ): FileRead {
        return route.on(path).readFile(path, maximumBytes)
    }

    override fun names(directory: String): List<String>? {
        return route.on(directory).names(directory)
    }

    override fun canonical(path: String): String? {
        return route.on(path).canonical(path)
    }

    override fun isWritableDirectory(path: String): Boolean {
        return route.on(path).isWritableDirectory(path)
    }
}

private class RoutedWrites(
    private val route: FolderRoute,
) : FolderWrites {
    override fun writeExclusive(
        path: String,
        bytes: ByteArray,
    ): ExclusiveWrite {
        return route.on(path).writeExclusive(path, bytes)
    }

    override fun replace(
        path: String,
        bytes: ByteArray,
    ): Boolean {
        return route.on(path).replace(path, bytes)
    }

    override fun delete(path: String): Boolean {
        return route.on(path).delete(path)
    }

    override fun deleteTree(path: String): Boolean {
        return route.on(path).deleteTree(path)
    }

    override fun createPrivateDirectories(path: String): Boolean {
        return route.on(path).createPrivateDirectories(path)
    }

    override fun createDirectories(path: String): Boolean {
        return route.on(path).createDirectories(path)
    }

    override fun restrictToOwner(path: String) {
        route.on(path).restrictToOwner(path)
    }
}
