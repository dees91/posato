package app.posato.di

import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.folder.AppleSyncPorts
import app.posato.feature.sync.folder.FolderSync
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.NioFolderFileSystem
import app.posato.feature.sync.macos.MacOsBootstrapCloudAdapter
import app.posato.feature.sync.macos.MacOsBootstrapKeychainAdapter
import app.posato.feature.sync.macos.MacOsMailboxAdapter
import app.posato.feature.sync.macos.MaintenanceCompanionTransport
import app.posato.feature.sync.macos.SyncCompanionTransport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import java.nio.file.Paths

/** Folder sync on every desktop host; a Mac keeps iCloud until a folder is chosen (ADR 0010). */
internal interface DesktopFolderSyncBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFolderSync(
        databasePath: String,
        companion: MaintenanceCompanionTransport,
    ): FolderSync {
        val transport: SyncCompanionTransport = companion
        val keys = MacOsBootstrapKeychainAdapter(transport)
        val apple = AppleSyncPorts(keys, MacOsBootstrapCloudAdapter(transport), keys, MacOsMailboxAdapter(transport))
        val localDirectory = Paths.get(databasePath).toAbsolutePath().parent.toString()
        return FolderSync(
            localDirectory,
            JdkSyncCryptoProvider(),
            apple.takeIf {
                isMacOs()
            },
            NioFolderFileSystem,
            Dispatchers.IO,
            System::currentTimeMillis,
        )
    }

    @Provides
    fun provideFolderSyncControls(folderSync: FolderSync): FolderSyncControls {
        return folderSync
    }
}

private fun isMacOs(): Boolean {
    return System.getProperty("os.name").orEmpty().startsWith("Mac")
}
