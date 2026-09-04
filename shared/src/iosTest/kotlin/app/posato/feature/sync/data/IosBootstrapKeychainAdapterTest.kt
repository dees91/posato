package app.posato.feature.sync.data

import app.posato.feature.sync.bootstrap.ACCOUNT_BINDING_BYTES
import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.KEYCHAIN_ITEM_BYTES
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemCreateResult
import app.posato.feature.sync.bootstrap.KeyItemDeleteResult
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceKeyItem
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IosBootstrapKeychainAdapterTest {
    @Test
    fun `given an available exact binding when resolved then the equal binding is available`() = runTest {
        val bytes = ByteArray(ACCOUNT_BINDING_BYTES) { index -> index.toByte() }
        val provider = FakeIosKeychainProvider(binding = IosKeychainBinding(IosKeychainBindingStatus.Available, bytes.toKeychainNSData()))

        val expected = BindingResolution.Available(checkNotNull(AccountBinding.fromBytes(bytes)))

        assertEquals(expected, IosBootstrapKeychainAdapter(provider).resolveBinding())
    }

    @Test
    fun `given an available wrong length binding when resolved then the outcome is undetermined without copying`() = runTest {
        val provider = FakeIosKeychainProvider(
            binding = IosKeychainBinding(IosKeychainBindingStatus.Available, UncopiedWrongLengthNSData(ACCOUNT_BINDING_BYTES - 1)),
        )

        assertEquals(BindingResolution.Undetermined, IosBootstrapKeychainAdapter(provider).resolveBinding())
    }

    @Test
    fun `given an available absent binding when resolved then the outcome is undetermined`() = runTest {
        val provider = FakeIosKeychainProvider(binding = IosKeychainBinding(IosKeychainBindingStatus.Available, null))

        assertEquals(BindingResolution.Undetermined, IosBootstrapKeychainAdapter(provider).resolveBinding())
    }

    @Test
    fun `given a non available binding when resolved then the outcome matches`() = runTest {
        val cases = listOf(
            IosKeychainBindingStatus.Unavailable to BindingResolution.Unavailable,
            IosKeychainBindingStatus.Restricted to BindingResolution.Restricted,
            IosKeychainBindingStatus.Undetermined to BindingResolution.Undetermined,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosKeychainProvider(binding = IosKeychainBinding(status, null))

            assertEquals(expected, IosBootstrapKeychainAdapter(provider).resolveBinding())
        }
    }

    @Test
    fun `given a found exact item when read then the byte identical value is found`() = runTest {
        val bytes = ByteArray(KEYCHAIN_ITEM_BYTES) { index -> index.toByte() }
        val provider = FakeIosKeychainProvider(itemRead = IosKeychainItemRead(IosKeychainReadStatus.Found, bytes.toKeychainNSData()))

        assertEquals(
            KeyItemReadResult.Found(checkNotNull(WorkspaceKeyItem.fromBytes(bytes))),
            IosBootstrapKeychainAdapter(provider).readItem(testBinding(), testAccount()),
        )
    }

    @Test
    fun `given a found wrong length item when read then integrity fails without copying`() = runTest {
        val provider = FakeIosKeychainProvider(
            itemRead = IosKeychainItemRead(IosKeychainReadStatus.Found, UncopiedWrongLengthNSData(KEYCHAIN_ITEM_BYTES + 1)),
        )

        assertEquals(KeyItemReadResult.IntegrityFailure, IosBootstrapKeychainAdapter(provider).readItem(testBinding(), testAccount()))
    }

    @Test
    fun `given a found absent item when read then integrity fails`() = runTest {
        val provider = FakeIosKeychainProvider(itemRead = IosKeychainItemRead(IosKeychainReadStatus.Found, null))

        assertEquals(KeyItemReadResult.IntegrityFailure, IosBootstrapKeychainAdapter(provider).readItem(testBinding(), testAccount()))
    }

    @Test
    fun `given a non found item outcome when read then the outcome matches`() = runTest {
        val cases = listOf(
            IosKeychainReadStatus.Missing to KeyItemReadResult.Missing,
            IosKeychainReadStatus.Retryable to KeyItemReadResult.Retryable,
            IosKeychainReadStatus.AccountChanged to KeyItemReadResult.AccountChanged,
            IosKeychainReadStatus.UnknownOutcome to KeyItemReadResult.UnknownOutcome,
            IosKeychainReadStatus.IntegrityFailure to KeyItemReadResult.IntegrityFailure,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosKeychainProvider(itemRead = IosKeychainItemRead(status, null))

            assertEquals(expected, IosBootstrapKeychainAdapter(provider).readItem(testBinding(), testAccount()))
        }
    }

    @Test
    fun `given a read when executed then the binding and account are forwarded`() = runTest {
        val provider = FakeIosKeychainProvider(itemRead = IosKeychainItemRead(IosKeychainReadStatus.Missing, null))
        val binding = testBinding()
        val account = testAccount()

        assertEquals(KeyItemReadResult.Missing, IosBootstrapKeychainAdapter(provider).readItem(binding, account))
        assertContentEquals(binding.copyBytes(), provider.seenBindings.single())
        assertEquals(listOf(account.text), provider.seenAccounts)
    }

    @Test
    fun `given a create outcome when created then the outcome matches`() = runTest {
        val cases = listOf(
            IosKeychainCreateStatus.Created to KeyItemCreateResult.Created,
            IosKeychainCreateStatus.AlreadyExists to KeyItemCreateResult.AlreadyExists,
            IosKeychainCreateStatus.Retryable to KeyItemCreateResult.Retryable,
            IosKeychainCreateStatus.AccountChanged to KeyItemCreateResult.AccountChanged,
            IosKeychainCreateStatus.UnknownOutcome to KeyItemCreateResult.UnknownOutcome,
            IosKeychainCreateStatus.IntegrityFailure to KeyItemCreateResult.IntegrityFailure,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosKeychainProvider(createStatus = status)

            assertEquals(expected, IosBootstrapKeychainAdapter(provider).createItem(testBinding(), testAccount(), testItem()))
        }
    }

    @Test
    fun `given a value when created then the binding account and value bytes are forwarded`() = runTest {
        val provider = FakeIosKeychainProvider(createStatus = IosKeychainCreateStatus.Created)
        val binding = testBinding()
        val account = testAccount()
        val item = testItem()

        assertEquals(KeyItemCreateResult.Created, IosBootstrapKeychainAdapter(provider).createItem(binding, account, item))
        assertContentEquals(binding.copyBytes(), provider.seenBindings.single())
        assertEquals(listOf(account.text), provider.seenAccounts)
        assertContentEquals(item.copyBytes(), provider.seenValues.single())
    }

    @Test
    fun `given a delete outcome when deleted then the outcome matches`() = runTest {
        val cases = listOf(
            IosKeychainDeleteStatus.DeletedAndAbsent to KeyItemDeleteResult.DeletedAndAbsent,
            IosKeychainDeleteStatus.Retryable to KeyItemDeleteResult.Retryable,
            IosKeychainDeleteStatus.AccountChanged to KeyItemDeleteResult.AccountChanged,
            IosKeychainDeleteStatus.UnknownOutcome to KeyItemDeleteResult.UnknownOutcome,
            IosKeychainDeleteStatus.IntegrityFailure to KeyItemDeleteResult.IntegrityFailure,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosKeychainProvider(deleteStatus = status)

            assertEquals(expected, IosBootstrapKeychainAdapter(provider).deleteItemAndVerifyAbsent(testBinding(), testAccount()))
        }
    }

    @Test
    fun `given a delete when executed then the binding and account are forwarded`() = runTest {
        val provider = FakeIosKeychainProvider(deleteStatus = IosKeychainDeleteStatus.DeletedAndAbsent)
        val binding = testBinding()
        val account = testAccount()

        assertEquals(KeyItemDeleteResult.DeletedAndAbsent, IosBootstrapKeychainAdapter(provider).deleteItemAndVerifyAbsent(binding, account))
        assertContentEquals(binding.copyBytes(), provider.seenBindings.single())
        assertEquals(listOf(account.text), provider.seenAccounts)
    }

    @Test
    fun `given the new keychain carriers when described then values stay redacted`() {
        assertEquals("IosKeychainBinding(redacted)", IosKeychainBinding(IosKeychainBindingStatus.Available, null).toString())
        assertEquals("IosKeychainItemRead(redacted)", IosKeychainItemRead(IosKeychainReadStatus.Found, null).toString())
    }

    @Test
    fun `given forwarded bytes when compared then copies match the originals`() = runTest {
        val provider = FakeIosKeychainProvider(itemRead = IosKeychainItemRead(IosKeychainReadStatus.Missing, null))
        val binding = testBinding()

        IosBootstrapKeychainAdapter(provider).readItem(binding, testAccount())

        assertContentEquals(binding.copyBytes(), provider.seenBindings.single())
    }
}

private fun testBinding(): AccountBinding {
    return checkNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { index -> index.toByte() }))
}

private fun testAccount(): KeyAccount {
    return checkNotNull(KeyAccount.fromText("123e4567-e89b-42d3-a456-426614174000"))
}

private fun testItem(): WorkspaceKeyItem {
    return checkNotNull(WorkspaceKeyItem.fromBytes(ByteArray(KEYCHAIN_ITEM_BYTES) { index -> index.toByte() }))
}

private class UncopiedWrongLengthNSData(
    private val reportedLength: Int,
) : NSData() {
    override fun length(): ULong = reportedLength.toULong()

    override fun bytes(): CPointer<out CPointed>? {
        error("Wrong-sized native data must not be copied")
    }
}

private class FakeIosKeychainProvider(
    var binding: IosKeychainBinding = IosKeychainBinding(IosKeychainBindingStatus.Undetermined, null),
    var itemRead: IosKeychainItemRead = IosKeychainItemRead(IosKeychainReadStatus.Retryable, null),
    var createStatus: IosKeychainCreateStatus = IosKeychainCreateStatus.Retryable,
    var deleteStatus: IosKeychainDeleteStatus = IosKeychainDeleteStatus.Retryable,
) : IosKeychainProvider {
    val seenBindings = mutableListOf<ByteArray>()
    val seenAccounts = mutableListOf<String>()
    val seenValues = mutableListOf<ByteArray>()

    override fun resolveBinding(): IosKeychainBinding = binding

    override fun readItem(
        binding: NSData,
        account: String,
    ): IosKeychainItemRead {
        seenBindings += binding.copyBytes()
        seenAccounts += account
        return itemRead
    }

    override fun createItem(
        binding: NSData,
        account: String,
        value: NSData,
    ): IosKeychainCreateStatus {
        seenBindings += binding.copyBytes()
        seenAccounts += account
        seenValues += value.copyBytes()
        return createStatus
    }

    override fun deleteItemAndVerifyAbsent(
        binding: NSData,
        account: String,
    ): IosKeychainDeleteStatus {
        seenBindings += binding.copyBytes()
        seenAccounts += account
        return deleteStatus
    }
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toKeychainNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(BetaInteropApi::class)
private fun NSData.copyBytes(): ByteArray {
    val result = ByteArray(length.toInt())
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
