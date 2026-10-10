package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.BootstrapKeyPort
import app.posato.feature.sync.bootstrap.KEYCHAIN_ITEM_BYTES
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemCreateResult
import app.posato.feature.sync.bootstrap.KeyItemDeleteResult
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceKeyItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val KEY_SUFFIX: String = ".key"
private const val MAXIMUM_STORED_KEY_BYTES: Int = 4_096

/** The workspace key item in a file only this user can read, sealed by [protection] where the platform offers it. */
internal class FolderKeyStore(
    private val localDirectory: String,
    private val files: FolderFileSystem,
    private val ioDispatcher: CoroutineDispatcher,
    private val protection: KeyItemProtection,
) : BootstrapKeyPort {
    private suspend fun <T> io(block: suspend () -> T): T {
        return withContext(ioDispatcher) { block() }
    }

    override suspend fun readItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemReadResult {
        return io {
            val stored = when (val read = files.readFile(keyFile(account), MAXIMUM_STORED_KEY_BYTES)) {
                FileRead.Missing -> return@io KeyItemReadResult.Missing
                FileRead.Failed -> return@io KeyItemReadResult.Retryable
                is FileRead.Found -> read.bytes
            }
            val item = protection.open(stored)?.let(WorkspaceKeyItem::fromBytes) ?: return@io KeyItemReadResult.IntegrityFailure
            KeyItemReadResult.Found(item)
        }
    }

    override suspend fun createItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
        value: WorkspaceKeyItem,
    ): KeyItemCreateResult {
        return io {
            val item = value.copyBytes()
            try {
                if (item.size != KEYCHAIN_ITEM_BYTES) return@io KeyItemCreateResult.IntegrityFailure
                val sealed = protection.seal(item) ?: return@io KeyItemCreateResult.IntegrityFailure
                if (!files.createPrivateDirectories(keyFile(account).parentPath())) return@io KeyItemCreateResult.Retryable
                when (files.writeExclusive(keyFile(account), sealed)) {
                    ExclusiveWrite.CREATED -> KeyItemCreateResult.Created.also { files.restrictToOwner(keyFile(account)) }
                    ExclusiveWrite.EXISTS -> KeyItemCreateResult.AlreadyExists
                    ExclusiveWrite.FAILED -> KeyItemCreateResult.Retryable
                }
            } finally {
                item.fill(0)
            }
        }
    }

    override suspend fun deleteItemAndVerifyAbsent(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemDeleteResult {
        return io {
            val file = keyFile(account)
            if (files.delete(file) && !files.isFile(file)) KeyItemDeleteResult.DeletedAndAbsent else KeyItemDeleteResult.Retryable
        }
    }

    private fun keyFile(account: KeyAccount): String {
        return localDirectory.child("keys").child(account.text + KEY_SUFFIX)
    }
}
