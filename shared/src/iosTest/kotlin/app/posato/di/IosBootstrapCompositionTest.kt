package app.posato.di

import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementOutcome
import app.posato.feature.enforcement.IosEnforcementProvider
import app.posato.feature.enforcement.IosEnforcementRequest
import app.posato.feature.enforcement.IosExpiryReconciliation
import app.posato.feature.enforcement.IosSessionEnforcement
import app.posato.feature.enforcement.IosSuspendedExpiry
import app.posato.feature.enforcement.IosSuspendedExpiryOutcome
import app.posato.feature.enforcement.IosSuspendedExpiryProvider
import app.posato.feature.enforcement.IosSuspendedExpiryRequest
import app.posato.feature.onboarding.UnavailableApplicationAccess
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.BootstrapResult
import app.posato.feature.sync.data.IosBootstrapKeychainAdapter
import app.posato.feature.sync.data.IosCloudAnchorCreateStatus
import app.posato.feature.sync.data.IosCloudAnchorRead
import app.posato.feature.sync.data.IosCloudAnchorReadStatus
import app.posato.feature.sync.data.IosCloudBundleSaveStatus
import app.posato.feature.sync.data.IosCloudChangeFetchStatus
import app.posato.feature.sync.data.IosCloudChangePage
import app.posato.feature.sync.data.IosCloudKitMailboxProvider
import app.posato.feature.sync.data.IosCloudZoneDeleteStatus
import app.posato.feature.sync.data.IosCloudZoneFetchStatus
import app.posato.feature.sync.data.IosCloudZoneSaveStatus
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosKeychainBinding
import app.posato.feature.sync.data.IosKeychainBindingStatus
import app.posato.feature.sync.data.IosKeychainCreateStatus
import app.posato.feature.sync.data.IosKeychainDeleteStatus
import app.posato.feature.sync.data.IosKeychainItemRead
import app.posato.feature.sync.data.IosKeychainProvider
import app.posato.feature.sync.data.IosKeychainReadStatus
import app.posato.feature.sync.data.IosSigningKey
import app.posato.feature.sync.data.UnavailableIosKeychainProvider
import app.posato.feature.targets.data.IosApplicationMappingsObservation
import app.posato.feature.targets.data.IosApplicationMappingsOperation
import app.posato.feature.targets.data.IosApplicationMappingsProvider
import app.posato.feature.targets.data.IosApplicationMappingsResponse
import app.posato.feature.targets.data.IosLocalApplicationMappings
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class IosBootstrapCompositionTest {
    @Test
    fun `given repeated ios runtime creation then the process graph and core are retained`() {
        val first = createIosApplicationRuntime(
            InertCryptoProvider(),
            InertMappingsProvider(),
            InertEnforcementProvider(),
            InertSuspendedExpiryProvider(),
            InertKeychainProvider(),
            InertMailboxProvider(),
        )
        val second = createIosApplicationRuntime(
            InertCryptoProvider(),
            InertMappingsProvider(),
            InertEnforcementProvider(),
            InertSuspendedExpiryProvider(),
            InertKeychainProvider(),
            InertMailboxProvider(),
        )

        assertSame(first, second)
        assertSame(first.applicationGraph, second.applicationGraph)
        assertSame(first.syncOperationCore, second.syncOperationCore)
    }

    @Test
    fun `given the real ios graph when resolved twice then the bootstrap facade is a singleton`() = runTest {
        val graph = compositionGraph()

        assertSame(graph.appleBootstrap, graph.appleBootstrap)
    }

    @Test
    fun `given the real ios graph when inspected then bootstrap runs on the io dispatcher`() = runTest {
        val graph = compositionGraph()

        assertSame(Dispatchers.IO, graph.appleBootstrap.backgroundDispatcher)
    }

    @Test
    fun `given an undetermined binding when sync runs then a truthful non-ready outcome is reported`() = runTest {
        val graph = compositionGraph()

        val result = graph.appleBootstrap.syncWithIcloud()

        assertIs<BootstrapResult.Retryable>(result)
    }

    @Test
    fun `given no swift keychain provider when resolved then the outcome stays non-ready`() = runTest {
        val adapter = IosBootstrapKeychainAdapter(UnavailableIosKeychainProvider)

        assertEquals(BindingResolution.Undetermined, adapter.resolveBinding())
    }

    private fun compositionGraph(): IosApplicationGraph {
        return createGraphFactory<IosApplicationGraph.Factory>().create(
            IosLocalApplicationMappings(InertMappingsProvider()),
            IosSessionEnforcement(
                IosEnforcement(InertEnforcementProvider()),
                IosSuspendedExpiry(InertSuspendedExpiryProvider()),
            ),
            InertKeychainProvider(),
            InertMailboxProvider(),
            InertCryptoProvider(),
            UnavailableApplicationAccess,
        )
    }
}

private class InertMappingsProvider : IosApplicationMappingsProvider {
    override fun load(completion: (IosApplicationMappingsResponse) -> Unit) {
        throw UnsupportedOperationException()
    }

    override fun choose(completion: (IosApplicationMappingsResponse) -> Unit): IosApplicationMappingsOperation {
        throw UnsupportedOperationException()
    }

    override fun requestAuthorization(completion: (IosApplicationMappingsResponse) -> Unit): IosApplicationMappingsOperation {
        throw UnsupportedOperationException()
    }

    override fun remove(
        identifier: String,
        completion: (IosApplicationMappingsResponse) -> Unit,
    ) {
        throw UnsupportedOperationException()
    }

    override fun clear(completion: (IosApplicationMappingsResponse) -> Unit) {
        throw UnsupportedOperationException()
    }

    override fun observeInvalidations(handler: () -> Unit): IosApplicationMappingsObservation {
        throw UnsupportedOperationException()
    }
}

private class InertEnforcementProvider : IosEnforcementProvider {
    override fun apply(
        request: IosEnforcementRequest,
        completion: (IosEnforcementOutcome) -> Unit,
    ) {
        throw UnsupportedOperationException()
    }

    override fun clear(handler: (IosEnforcementOutcome) -> Unit) {
        throw UnsupportedOperationException()
    }

    override fun status(handler: (IosEnforcementOutcome) -> Unit) {
        throw UnsupportedOperationException()
    }
}

private class InertSuspendedExpiryProvider : IosSuspendedExpiryProvider {
    override fun schedule(
        request: IosSuspendedExpiryRequest,
        completion: (IosSuspendedExpiryOutcome) -> Unit,
    ) {
        throw UnsupportedOperationException()
    }

    override fun cancel(handler: (IosSuspendedExpiryOutcome) -> Unit) {
        throw UnsupportedOperationException()
    }

    override fun readReconciliation(
        sessionId: String,
        handler: (IosExpiryReconciliation) -> Unit,
    ) {
        throw UnsupportedOperationException()
    }
}

private class InertKeychainProvider : IosKeychainProvider {
    override fun resolveBinding(): IosKeychainBinding {
        return IosKeychainBinding(IosKeychainBindingStatus.Undetermined, null)
    }

    override fun readItem(
        binding: NSData,
        account: String,
    ): IosKeychainItemRead {
        return IosKeychainItemRead(IosKeychainReadStatus.UnknownOutcome, null)
    }

    override fun createItem(
        binding: NSData,
        account: String,
        value: NSData,
    ): IosKeychainCreateStatus {
        return IosKeychainCreateStatus.UnknownOutcome
    }

    override fun deleteItemAndVerifyAbsent(
        binding: NSData,
        account: String,
    ): IosKeychainDeleteStatus {
        return IosKeychainDeleteStatus.UnknownOutcome
    }
}

private class InertMailboxProvider : IosCloudKitMailboxProvider {
    override fun fetchZone(binding: NSData): IosCloudZoneFetchStatus {
        return IosCloudZoneFetchStatus.Retryable
    }

    override fun saveZone(binding: NSData): IosCloudZoneSaveStatus {
        return IosCloudZoneSaveStatus.Retryable
    }

    override fun readAnchor(binding: NSData): IosCloudAnchorRead {
        return IosCloudAnchorRead(IosCloudAnchorReadStatus.Retryable, null)
    }

    override fun createAnchor(
        binding: NSData,
        fields: NSData,
    ): IosCloudAnchorCreateStatus {
        return IosCloudAnchorCreateStatus.Retryable
    }

    override fun saveBundle(
        binding: NSData,
        identifier: NSData,
        payload: NSData,
    ): IosCloudBundleSaveStatus {
        return IosCloudBundleSaveStatus.Retryable
    }

    override fun fetchChanges(
        binding: NSData,
        cursor: NSData,
    ): IosCloudChangePage {
        return IosCloudChangePage(IosCloudChangeFetchStatus.Retryable, false, null, null, null)
    }

    override fun deleteZoneAndVerifyAbsent(binding: NSData): IosCloudZoneDeleteStatus {
        return IosCloudZoneDeleteStatus.Retryable
    }

    override fun cancelInflight() = Unit
}

private class InertCryptoProvider : IosCryptoProvider {
    override fun randomBytes(count: Int): NSData? {
        return null
    }

    override fun sha256(message: NSData): NSData? {
        return null
    }

    override fun hmacSha256(
        key: NSData,
        message: NSData,
    ): NSData? {
        return null
    }

    override fun sealAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        plaintext: NSData,
    ): NSData? {
        return null
    }

    override fun openAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        ciphertextAndTag: NSData,
    ): NSData? {
        return null
    }

    override fun createSigningKey(): IosSigningKey? {
        return null
    }

    override fun verifyEd25519(
        publicKey: NSData,
        message: NSData,
        signature: NSData,
    ): Boolean {
        return false
    }
}
