package app.posato.di

import app.posato.feature.sync.data.IosBootstrapCloudAdapter
import app.posato.feature.sync.data.IosBootstrapKeychainAdapter
import app.posato.feature.sync.data.IosCloudKitMailboxProvider
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosKeychainProvider
import app.posato.feature.sync.data.IosMailboxAdapter
import app.posato.feature.sync.data.IosSyncCryptoProvider
import app.posato.feature.sync.folder.AppleSyncPorts
import app.posato.feature.sync.folder.BookmarkFolderAccess
import app.posato.feature.sync.folder.FolderSync
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.FoundationFolderFileSystem
import app.posato.feature.sync.folder.IosFolderPicker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.posix.time
import kotlin.coroutines.resume

/** Folder sync on iOS: the Files picker chooses the folder, and iCloud stays until one is chosen (ADR 0010). */
internal interface IosFolderSyncBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFolderSync(
        keychainProvider: IosKeychainProvider,
        mailboxProvider: IosCloudKitMailboxProvider,
        cryptoProvider: IosCryptoProvider,
        folderPicker: IosFolderPicker,
    ): FolderSync {
        val keys = IosBootstrapKeychainAdapter(keychainProvider)
        val apple = AppleSyncPorts(keys, IosBootstrapCloudAdapter(mailboxProvider), keys, IosMailboxAdapter(mailboxProvider))
        return FolderSync(
            localDirectory = iosApplicationSupportDirectory(),
            crypto = IosSyncCryptoProvider(cryptoProvider),
            apple = apple,
            files = FoundationFolderFileSystem,
            ioDispatcher = Dispatchers.IO,
            now = { time(null) * MILLIS_PER_SECOND },
            access = BookmarkFolderAccess(),
            browser = { pickFolder(folderPicker) },
            pollsWhileRunning = false,
            acceptsTypedPath = false,
        )
    }

    @Provides
    fun provideFolderSyncControls(folderSync: FolderSync): FolderSyncControls {
        return folderSync
    }
}

private suspend fun pickFolder(picker: IosFolderPicker): String? {
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation -> picker.pickFolder { token -> continuation.resume(token) } }
    }
}
