package app.posato.feature.sync.folder

import app.posato.feature.sync.data.SyncCryptoProvider
import kotlinx.coroutines.Dispatchers
import java.nio.file.Path

internal fun nioPorts(
    root: () -> Path?,
    local: Path,
    crypto: SyncCryptoProvider,
): FolderSyncPorts {
    return FolderSyncPorts({ root()?.toString() }, local.toString(), crypto, NioFolderFileSystem, Dispatchers.IO)
}
