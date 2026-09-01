package app.posato.feature.sync.domain

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.DurableClockState
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.ImmutableBytes
import app.posato.feature.sync.data.OpaqueTransportProgress
import app.posato.feature.sync.data.PrepareBundleResult
import app.posato.feature.sync.data.PreparedStoredBundle
import app.posato.feature.sync.data.StoredAcceptedBundle
import app.posato.feature.sync.data.StoredStagedBundle
import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.data.SyncReplicaStore
import app.posato.feature.sync.data.SyncStoreResult
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ExactDomain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncWriterOpenTest {
    @Test
    fun `given an active writer when another open is rejected then the rejected transport key is cleared`() = runTest {
        val core = SyncOperationCore(FakeSyncReplicaStore(snapshot()), FakeSyncCryptoProvider(), SyncWallClock { 100 })
        val writer = assertIs<OpenSyncWriterResult.Success>(core.open(testContext, transportKey())).writer
        val rejectedKey = transportKey()

        try {
            val result = core.open(testContext, rejectedKey)

            assertEquals(OpenSyncWriterFailure.ALREADY_OPEN, assertIs<OpenSyncWriterResult.Failure>(result).reason)
            assertContentEquals(
                ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES),
                rejectedKey.useBytes { bytes -> bytes.copyOf() },
            )
        } finally {
            writer.close()
        }
    }

    @Test
    fun `given a suspended store open when opening is cancelled then the transport key is cleared`() = runTest {
        val openStarted = CompletableDeferred<Unit>()
        val store = FakeSyncReplicaStore(
            initial = snapshot(),
            beforeOpen = {
                openStarted.complete(Unit)
                awaitCancellation()
            },
        )
        val openingKey = transportKey()
        val openJob = launch {
            SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 }).open(testContext, openingKey)
        }
        openStarted.await()

        openJob.cancel()
        openJob.join()

        assertContentEquals(
            ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES),
            openingKey.useBytes { bytes -> bytes.copyOf() },
        )
    }
}

class SyncWriterTest {
    @Test
    fun `given bounded source bytes when wrapped then later source changes do not change the bundle`() {
        val source = byteArrayOf(1)
        val bundle = checkNotNull(EncryptedBundle.fromBytes(source))

        source[0] = 2

        assertContentEquals(byteArrayOf(1), bundle.copyBytes())
    }

    @Test
    fun `given the terminal author sequence when advanced then it cannot be reused`() {
        assertEquals(null, nextAuthorSequence(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, nextAuthorSequence(Long.MAX_VALUE - 1))
    }

    @Test
    fun `given wall clock samples and remaining counters when reserved then HLC advancement is monotonic and atomic`() {
        data class Case(
            val current: HybridLogicalClock,
            val wallTime: Long,
            val count: Int,
            val expected: List<HybridLogicalClock>?,
        )
        val terminal = SyncFormatLimits.MAX_LOGICAL_COUNTER
        val cases = listOf(
            Case(HybridLogicalClock(100, 5), 101, 2, listOf(HybridLogicalClock(101, 0), HybridLogicalClock(101, 1))),
            Case(HybridLogicalClock(100, 5), 100, 1, listOf(HybridLogicalClock(100, 6))),
            Case(HybridLogicalClock(100, 5), 99, 1, listOf(HybridLogicalClock(100, 6))),
            Case(HybridLogicalClock(100, terminal), 100, 1, listOf(HybridLogicalClock(101, 0))),
            Case(
                HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, terminal - 2),
                SyncFormatLimits.MAX_PHYSICAL_MILLIS,
                2,
                listOf(
                    HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, terminal - 1),
                    HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, terminal),
                ),
            ),
            Case(HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, terminal - 1), SyncFormatLimits.MAX_PHYSICAL_MILLIS, 2, null),
            Case(HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, terminal), SyncFormatLimits.MAX_PHYSICAL_MILLIS, 1, null),
        )

        cases.forEach { case ->
            assertEquals(case.expected, reserveLocalClocks(DurableClockState(case.current, false), case.wallTime, case.count))
        }
    }

    @Test
    fun `given cancellation after writer shutdown begins when release suspends then release completes`() = runTest {
        val initialSnapshot = snapshot()
        val releaseStarted = CompletableDeferred<Unit>()
        val continueRelease = CompletableDeferred<Unit>()
        var releaseCompleted = false
        val writer = SyncWriter(
            store = FakeSyncReplicaStore(initialSnapshot),
            cryptoProvider = FakeSyncCryptoProvider(),
            wallClock = SyncWallClock { 100 },
            transportKey = transportKey(),
            initialSnapshot = initialSnapshot,
            onClose = {
                releaseStarted.complete(Unit)
                continueRelease.await()
                releaseCompleted = true
            },
        )

        val closeJob = launch { writer.close() }
        releaseStarted.await()
        closeJob.cancel()
        continueRelease.complete(Unit)
        closeJob.join()

        assertTrue(releaseCompleted)
    }

    @Test
    fun `given cancellation during the first local commit when retried then the prepared incarnation is retired`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val store = FakeSyncReplicaStore(snapshot(), LocalCommitMode.CANCELLED)
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
        val before = store.current

        assertFailsWith<CancellationException> {
            writer.mutate(LocalSyncMutation.RemoveApplicationPolicy)
        }

        assertEquals(before, store.current)
        assertEquals(1, provider.signingKeyCloseCount)
        assertEquals(
            LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN,
            assertIs<LocalMutationResult.Failure>(writer.mutate(LocalSyncMutation.RemoveApplicationPolicy)).reason,
        )
    }

    @Test
    fun `given a fresh writer when the first mutation commits then registration and change are one pending batch`() = runTest {
        val store = FakeSyncReplicaStore(snapshot())
        val core = SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 })
        val opened = assertIs<OpenSyncWriterResult.Success>(core.open(testContext, transportKey()))

        val result = assertIs<LocalMutationResult.Success>(
            opened.writer.mutate(LocalSyncMutation.PresentDomain(checkNotNull(ExactDomain.restore("stable.example")))),
        )

        assertEquals(2, result.pendingBundles.size)
        assertEquals(2, store.current.acceptedBundles.size)
        assertEquals(2, store.current.pendingBundles.size)
        assertEquals(listOf(1L, 2L), store.current.acceptedBundles.values.map { it.operation.authorSequence }.sorted())
    }

    @Test
    fun `given an invalid session mutation when retried then the writer remains usable`() = runTest {
        val invalidSessions = listOf(
            LocalSyncMutation.StartSession(SessionId(testIdentifier(20)), 100, 100),
            LocalSyncMutation.StartSession(
                SessionId(testIdentifier(21)),
                100,
                100 + SyncFormatLimits.MAX_SESSION_DURATION_MILLIS + 1,
            ),
        )

        invalidSessions.forEach { invalidMutation ->
            val store = FakeSyncReplicaStore(snapshot())
            val writer = assertIs<OpenSyncWriterResult.Success>(
                SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 })
                    .open(testContext, transportKey()),
            ).writer
            val before = store.current

            val invalidResult = assertIs<LocalMutationResult.Failure>(writer.mutate(invalidMutation))

            assertEquals(LocalMutationFailure.INVALID_MUTATION, invalidResult.reason)
            assertEquals(before, store.current)
            assertIs<LocalMutationResult.Success>(writer.mutate(LocalSyncMutation.RemoveApplicationPolicy))
        }
    }

    @Test
    fun `given insufficient HLC space for the complete first batch when mutated then terminal exhaustion is persisted without operations`() =
        runTest {
            val beforeTerminal = HybridLogicalClock(
                SyncFormatLimits.MAX_PHYSICAL_MILLIS,
                SyncFormatLimits.MAX_LOGICAL_COUNTER - 1,
            )
            val store = FakeSyncReplicaStore(snapshot(DurableClockState(beforeTerminal, false)))
            val core = SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { SyncFormatLimits.MAX_PHYSICAL_MILLIS })
            val opened = assertIs<OpenSyncWriterResult.Success>(core.open(testContext, transportKey()))

            val result = assertIs<LocalMutationResult.Failure>(
                opened.writer.mutate(LocalSyncMutation.RemoveApplicationPolicy),
            )

            assertEquals(LocalMutationFailure.HLC_EXHAUSTED, result.reason)
            assertEquals(
                HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, SyncFormatLimits.MAX_LOGICAL_COUNTER),
                store.current.clockState.last,
            )
            assertTrue(store.current.clockState.isExhausted)
            assertEquals(emptyMap(), store.current.acceptedBundles)
        }

    @Test
    fun `given ambiguous HLC exhaustion commits when reconciled then only exact or proven absent outcomes succeed`() = runTest {
        val beforeTerminal = HybridLogicalClock(
            SyncFormatLimits.MAX_PHYSICAL_MILLIS,
            SyncFormatLimits.MAX_LOGICAL_COUNTER - 1,
        )
        val cases = listOf(
            LocalCommitMode.AMBIGUOUS_EXACT to LocalMutationFailure.HLC_EXHAUSTED,
            LocalCommitMode.AMBIGUOUS_ABSENT_ONCE to LocalMutationFailure.HLC_EXHAUSTED,
            LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE to LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN,
        )

        cases.forEach { (mode, expectedFailure) ->
            val store = FakeSyncReplicaStore(snapshot(DurableClockState(beforeTerminal, false)), mode)
            val writer = assertIs<OpenSyncWriterResult.Success>(
                SyncOperationCore(
                    store,
                    FakeSyncCryptoProvider(),
                    SyncWallClock { SyncFormatLimits.MAX_PHYSICAL_MILLIS },
                ).open(testContext, transportKey()),
            ).writer

            val result = assertIs<LocalMutationResult.Failure>(
                writer.mutate(LocalSyncMutation.RemoveApplicationPolicy),
            )

            assertEquals(expectedFailure, result.reason, mode.name)
        }
    }

    @Test
    fun `given a rejected bundle and transport receipt when handled then only exact refetch advances progress`() = runTest {
        data class Case(
            val exactRefetchAvailable: Boolean,
            val remoteCommitMode: RemoteCommitMode,
        )
        val cases = listOf(
            Case(false, RemoteCommitMode.SUCCESS),
            Case(true, RemoteCommitMode.SUCCESS),
            Case(true, RemoteCommitMode.AMBIGUOUS_EXACT),
        )

        cases.forEachIndexed { index, case ->
            val provider = FakeSyncCryptoProvider()
            val store = FakeSyncReplicaStore(snapshot(), remoteCommitMode = case.remoteCommitMode)
            val writer = assertIs<OpenSyncWriterResult.Success>(
                SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
            ).writer
            val progress = OpaqueTransportProgress(byteArrayOf(index.toByte()))

            val result = writer.acceptRemote(
                remoteBundle(provider, testOperation(2, 2, SyncOperationPayload.AuthorRegister)).copyBytes(),
                RemoteTransportReceipt(progress, case.exactRefetchAvailable),
            )

            assertEquals(RemoteAcceptanceFailure.INVALID_OPERATION, assertIs<RemoteAcceptanceResult.Failure>(result).reason)
            assertEquals(emptyMap(), store.current.stagedBundles)
            assertEquals(progress.takeIf { case.exactRefetchAvailable }, store.current.transportProgress)
        }
    }

    @Test
    fun `given remote bytes at the bundle limit when accepted then oversized input is rejected before parsing`() = runTest {
        val store = FakeSyncReplicaStore(snapshot())
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
        val receipt = RemoteTransportReceipt(OpaqueTransportProgress(byteArrayOf(1)), exactRefetchAvailable = true)

        val atLimit = writer.acceptRemote(ByteArray(SyncFormatLimits.COMPLETE_BUNDLE_BYTES), RemoteTransportReceipt(null, false))
        val oversizedWithoutProof = writer.acceptRemote(
            ByteArray(SyncFormatLimits.COMPLETE_BUNDLE_BYTES + 1),
            receipt.copy(exactRefetchAvailable = false),
        )
        val progressWithoutProof = store.current.transportProgress
        val oversizedWithProof = writer.acceptRemote(ByteArray(SyncFormatLimits.COMPLETE_BUNDLE_BYTES + 1), receipt)

        assertEquals(RemoteAcceptanceFailure.MALFORMED, assertIs<RemoteAcceptanceResult.Failure>(atLimit).reason)
        assertEquals(RemoteAcceptanceFailure.OVERSIZED, assertIs<RemoteAcceptanceResult.Failure>(oversizedWithoutProof).reason)
        assertEquals(null, progressWithoutProof)
        assertEquals(RemoteAcceptanceFailure.OVERSIZED, assertIs<RemoteAcceptanceResult.Failure>(oversizedWithProof).reason)
        assertEquals(receipt.progress, store.current.transportProgress)
    }

    @Test
    fun `given rejected progress cannot be reconciled when handled then the writer freezes`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val store = FakeSyncReplicaStore(snapshot(), remoteCommitMode = RemoteCommitMode.AMBIGUOUS_WITH_EXTRA_STATE)
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
        val rejected = remoteBundle(provider, testOperation(2, 2, SyncOperationPayload.AuthorRegister))

        val result = writer.acceptRemote(
            rejected.copyBytes(),
            RemoteTransportReceipt(OpaqueTransportProgress(byteArrayOf(9)), exactRefetchAvailable = true),
        )

        assertEquals(
            RemoteAcceptanceFailure.LOCAL_COMMIT_UNCERTAIN,
            assertIs<RemoteAcceptanceResult.Failure>(result).reason,
        )
        assertEquals(
            RemoteAcceptanceFailure.LOCAL_COMMIT_UNCERTAIN,
            assertIs<RemoteAcceptanceResult.Failure>(
                writer.acceptRemote(rejected.copyBytes(), RemoteTransportReceipt(null, exactRefetchAvailable = false)),
            ).reason,
        )
    }

    @Test
    fun `given business arrives before registration when both are accepted then staged input and progress converge atomically`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val store = FakeSyncReplicaStore(snapshot())
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
        val domain = checkNotNull(ExactDomain.restore("stable.example"))

        assertIs<RemoteAcceptanceResult.Staged>(
            writer.acceptRemote(
                remoteBundle(provider, testOperation(2, 2, SyncOperationPayload.DomainPresent(domain))).copyBytes(),
                RemoteTransportReceipt(OpaqueTransportProgress(byteArrayOf(1)), false),
            ),
        )
        val result = writer.acceptRemote(
            remoteBundle(provider, testOperation(1, 1, SyncOperationPayload.AuthorRegister)).copyBytes(),
            RemoteTransportReceipt(OpaqueTransportProgress(byteArrayOf(2)), false),
        )

        assertIs<RemoteAcceptanceResult.Accepted>(result)
        assertEquals(listOf(domain), writer.projection().domains)
        assertEquals(emptyMap(), store.current.stagedBundles)
        assertEquals(OpaqueTransportProgress(byteArrayOf(2)), store.current.transportProgress)
    }

    @Test
    fun `given an ambiguous local commit with the exact footprint when reconciled then immutable bytes succeed`() = runTest {
        val store = FakeSyncReplicaStore(snapshot(), LocalCommitMode.AMBIGUOUS_EXACT)
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer

        val result = writer.mutate(LocalSyncMutation.RemoveApplicationPolicy)

        assertIs<LocalMutationResult.Success>(result)
        assertEquals(2, store.current.pendingBundles.size)
    }

    @Test
    fun `given an ambiguous local commit with extra state when reconciled then the writer freezes`() = runTest {
        val store = FakeSyncReplicaStore(snapshot(), LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE)
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer

        val result = assertIs<LocalMutationResult.Failure>(writer.mutate(LocalSyncMutation.RemoveApplicationPolicy))

        assertEquals(LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN, result.reason)
        assertEquals(
            LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN,
            assertIs<LocalMutationResult.Failure>(writer.mutate(LocalSyncMutation.RemoveApplicationPolicy)).reason,
        )
    }

    @Test
    fun `given persisted plaintext bytes do not match the authenticated bundle when reopened then corruption is returned`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val operation = testOperation(1, 1, SyncOperationPayload.AuthorRegister)
        val bundle = remoteBundle(provider, operation)
        val corrupted = snapshot().copy(
            acceptedBundles = mapOf(
                operation.operationId to StoredAcceptedBundle(bundle, ImmutableBytes(byteArrayOf(1)), operation),
            ),
        )

        val result = SyncOperationCore(FakeSyncReplicaStore(corrupted), provider, SyncWallClock { 100 })
            .open(testContext, transportKey())

        assertEquals(OpenSyncWriterFailure.CORRUPTION, assertIs<OpenSyncWriterResult.Failure>(result).reason)
    }

    @Test
    fun `given exhausted state is not terminal when reopened then corruption is returned`() = runTest {
        val corrupted = snapshot(DurableClockState(HybridLogicalClock(10, 0), true))

        val result = SyncOperationCore(FakeSyncReplicaStore(corrupted), FakeSyncCryptoProvider(), SyncWallClock { 100 })
            .open(testContext, transportKey())

        assertEquals(OpenSyncWriterFailure.CORRUPTION, assertIs<OpenSyncWriterResult.Failure>(result).reason)
    }

    @Test
    fun `given accepted history when reopened then the durable HLC must cover every operation`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val acceptedClock = HybridLogicalClock(10, 5)
        val accepted = acceptedSnapshot(
            provider,
            listOf(testOperation(1, 1, SyncOperationPayload.AuthorRegister).copy(clock = acceptedClock)),
        )
        val cases = listOf(
            DurableClockState(HybridLogicalClock(10, 4), false) to false,
            DurableClockState(acceptedClock, false) to true,
            DurableClockState(HybridLogicalClock(10, 6), false) to true,
        )

        cases.forEach { (clockState, succeeds) ->
            val result = SyncOperationCore(
                FakeSyncReplicaStore(accepted.copy(clockState = clockState)),
                provider,
                SyncWallClock { 100 },
            ).open(testContext, transportKey())

            if (succeeds) {
                assertIs<OpenSyncWriterResult.Success>(result)
            } else {
                assertEquals(OpenSyncWriterFailure.CORRUPTION, assertIs<OpenSyncWriterResult.Failure>(result).reason)
            }
        }
    }

    @Test
    fun `given an invalid accepted author history when reopened then corruption is returned`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val cases = listOf(
            listOf(testOperation(30, 1, SyncOperationPayload.ApplicationPolicyAbsent)),
            listOf(
                testOperation(31, 1, SyncOperationPayload.AuthorRegister),
                testOperation(32, 2, SyncOperationPayload.AuthorRegister),
            ),
            listOf(
                testOperation(37, 1, SyncOperationPayload.AuthorRegister),
                testOperation(38, 2, SyncOperationPayload.ApplicationPolicyAbsent),
                testOperation(39, 2, SyncOperationPayload.ApplicationPolicyAbsent),
            ),
        )

        cases.forEach { operations ->
            val result = SyncOperationCore(
                FakeSyncReplicaStore(acceptedSnapshot(provider, operations)),
                provider,
                SyncWallClock { 100 },
            ).open(testContext, transportKey())

            assertEquals(OpenSyncWriterFailure.CORRUPTION, assertIs<OpenSyncWriterResult.Failure>(result).reason)
        }
    }

    @Test
    fun `given a valid accepted author history with a sequence gap when reopened then it succeeds`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val accepted = acceptedSnapshot(
            provider,
            listOf(
                testOperation(33, 1, SyncOperationPayload.AuthorRegister),
                testOperation(34, 3, SyncOperationPayload.ApplicationPolicyAbsent),
            ),
        )

        val result = SyncOperationCore(FakeSyncReplicaStore(accepted), provider, SyncWallClock { 100 })
            .open(testContext, transportKey())

        assertIs<OpenSyncWriterResult.Success>(result)
    }

    @Test
    fun `given an accepted author changes signing key when reopened then corruption is returned`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val changedKey = checkNotNull(PublicSigningKey.fromBytes(ByteArray(SyncFormatLimits.PUBLIC_KEY_BYTES) { 8 }))
        val changedKeyProvider = FakeSyncCryptoProvider(changedKey)
        val registration = testOperation(35, 1, SyncOperationPayload.AuthorRegister)
        val changedKeyOperation = testOperation(36, 2, SyncOperationPayload.ApplicationPolicyAbsent)
            .copy(publicSigningKey = changedKey)
        val accepted = snapshot(DurableClockState(changedKeyOperation.clock, false)).copy(
            acceptedBundles = listOf(
                storedAcceptedBundle(provider, registration),
                storedAcceptedBundle(changedKeyProvider, changedKeyOperation),
            ).associateBy { stored -> stored.operation.operationId },
        )

        val result = SyncOperationCore(FakeSyncReplicaStore(accepted), provider, SyncWallClock { 100 })
            .open(testContext, transportKey())

        assertEquals(OpenSyncWriterFailure.CORRUPTION, assertIs<OpenSyncWriterResult.Failure>(result).reason)
    }

    @Test
    fun `given invalid staged state when reopened then corruption is returned`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val registration = testOperation(40, 1, SyncOperationPayload.AuthorRegister)
        val overlappingRegistration = testOperation(45, 1, SyncOperationPayload.AuthorRegister)
        val cases = listOf(
            snapshot().withStagedBundles(
                provider,
                listOf(testOperation(41, 1, SyncOperationPayload.ApplicationPolicyAbsent)),
            ),
            acceptedSnapshot(provider, listOf(registration)).withStagedBundles(
                provider,
                listOf(testOperation(42, 2, SyncOperationPayload.ApplicationPolicyAbsent)),
            ),
            snapshot().withStagedBundles(
                provider,
                listOf(
                    testOperation(43, 2, SyncOperationPayload.ApplicationPolicyAbsent),
                    testOperation(44, 2, SyncOperationPayload.ApplicationPolicyAbsent),
                ),
            ),
            stagedSnapshot(provider, SyncFormatLimits.MAX_UNKNOWN_AUTHOR_BUNDLES + 1, singleAuthor = true),
            stagedSnapshot(provider, SyncFormatLimits.MAX_STAGED_BUNDLES + 1, singleAuthor = false),
            acceptedSnapshot(provider, listOf(overlappingRegistration)).withStagedBundles(
                provider,
                listOf(testOperation(45, 2, SyncOperationPayload.ApplicationPolicyAbsent, author = 11)),
            ),
        )

        cases.forEach { corrupted ->
            val result = SyncOperationCore(FakeSyncReplicaStore(corrupted), provider, SyncWallClock { 100 })
                .open(testContext, transportKey())

            assertEquals(OpenSyncWriterFailure.CORRUPTION, assertIs<OpenSyncWriterResult.Failure>(result).reason)
        }
    }

    @Test
    fun `given staged gaps and competing keys when reopened then it succeeds`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val competingKey = checkNotNull(PublicSigningKey.fromBytes(ByteArray(SyncFormatLimits.PUBLIC_KEY_BYTES) { 9 }))
        val competingProvider = FakeSyncCryptoProvider(competingKey)
        val first = testOperation(46, 2, SyncOperationPayload.ApplicationPolicyAbsent)
        val later = testOperation(47, 4, SyncOperationPayload.ApplicationPolicyAbsent).copy(publicSigningKey = competingKey)
        val staged = listOf(
            storedStagedBundle(provider, first),
            storedStagedBundle(competingProvider, later),
        ).associateBy { stored -> stored.operation.operationId }

        val result = SyncOperationCore(
            FakeSyncReplicaStore(snapshot().copy(stagedBundles = staged)),
            provider,
            SyncWallClock { 100 },
        ).open(testContext, transportKey())

        assertIs<OpenSyncWriterResult.Success>(result)
    }

    @Test
    fun `given an ambiguous remote commit with the exact footprint when reconciled then acceptance succeeds`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val store = FakeSyncReplicaStore(snapshot(), remoteCommitMode = RemoteCommitMode.AMBIGUOUS_EXACT)
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer

        val result = writer.acceptRemote(
            remoteBundle(provider, testOperation(1, 1, SyncOperationPayload.AuthorRegister)).copyBytes(),
            RemoteTransportReceipt(OpaqueTransportProgress(byteArrayOf(1)), false),
        )

        assertIs<RemoteAcceptanceResult.Accepted>(result)
    }

    @Test
    fun `given duplicate replay sequence conflict and a gap when accepted then each public outcome is stable`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val store = FakeSyncReplicaStore(snapshot())
        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
        val registration = remoteBundle(provider, testOperation(1, 1, SyncOperationPayload.AuthorRegister))
        assertIs<RemoteAcceptanceResult.Accepted>(
            writer.acceptRemote(registration.copyBytes(), RemoteTransportReceipt(null, false)),
        )

        val duplicateProgress = OpaqueTransportProgress(byteArrayOf(3))
        assertIs<RemoteAcceptanceResult.Duplicate>(
            writer.acceptRemote(registration.copyBytes(), RemoteTransportReceipt(duplicateProgress, false)),
        )
        assertEquals(duplicateProgress, store.current.transportProgress)
        val altered = registration.copyBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(
            RemoteAcceptanceFailure.REPLAY_CONFLICT,
            assertIs<RemoteAcceptanceResult.Failure>(
                writer.acceptRemote(altered, RemoteTransportReceipt(null, false)),
            ).reason,
        )
        assertEquals(
            RemoteAcceptanceFailure.SEQUENCE_CONFLICT,
            assertIs<RemoteAcceptanceResult.Failure>(
                writer.acceptRemote(
                    remoteBundle(provider, testOperation(2, 1, SyncOperationPayload.AuthorRegister)).copyBytes(),
                    RemoteTransportReceipt(null, false),
                ),
            ).reason,
        )
        val gapDomain = checkNotNull(ExactDomain.restore("gap.example"))
        assertIs<RemoteAcceptanceResult.Accepted>(
            writer.acceptRemote(
                remoteBundle(provider, testOperation(3, 3, SyncOperationPayload.DomainPresent(gapDomain))).copyBytes(),
                RemoteTransportReceipt(null, false),
            ),
        )
        assertEquals(emptyList(), writer.projection().domains)
    }

    @Test
    fun `given per-author or global staging capacity when deferred then only exact-refetch receipts advance progress`() = runTest {
        data class Case(
            val initial: SyncReplicaSnapshot,
            val author: Int,
            val exactRefetch: Boolean
        )
        val cases = listOf(true, false).flatMap { exactRefetch ->
            listOf(
                Case(stagedSnapshot(FakeSyncCryptoProvider(), SyncFormatLimits.MAX_UNKNOWN_AUTHOR_BUNDLES, true), 10, exactRefetch),
                Case(stagedSnapshot(FakeSyncCryptoProvider(), SyncFormatLimits.MAX_STAGED_BUNDLES, false), 500, exactRefetch),
            )
        }
        cases.forEachIndexed { index, case ->
            val provider = FakeSyncCryptoProvider()
            val store = FakeSyncReplicaStore(case.initial)
            val writer = assertIs<OpenSyncWriterResult.Success>(
                SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
            ).writer
            val progress = OpaqueTransportProgress(byteArrayOf(index.toByte()))
            val operation = testOperation(1_000 + index, 100, SyncOperationPayload.ApplicationPolicyAbsent, case.author)

            val result = writer.acceptRemote(
                remoteBundle(provider, operation).copyBytes(),
                RemoteTransportReceipt(progress, exactRefetchAvailable = case.exactRefetch),
            )

            assertEquals(
                RemoteAcceptanceFailure.DEFERRED_CAPACITY,
                assertIs<RemoteAcceptanceResult.Failure>(result).reason,
            )
            assertEquals(progress.takeIf { case.exactRefetch }, store.current.transportProgress)
        }
    }

    @Test
    fun `given remote clocks at the terminal boundary when accepted then terminal state persists and later local writes are refused`() {
        runTest {
            val terminal = HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, SyncFormatLimits.MAX_LOGICAL_COUNTER)
            listOf(terminal.copy(logicalCounter = terminal.logicalCounter - 1), terminal).forEachIndexed { index, remoteClock ->
                val provider = FakeSyncCryptoProvider()
                val store = FakeSyncReplicaStore(snapshot())
                val writer = assertIs<OpenSyncWriterResult.Success>(
                    SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
                ).writer
                val registration = testOperation(800 + index, 1, SyncOperationPayload.AuthorRegister).copy(clock = remoteClock)

                assertIs<RemoteAcceptanceResult.Accepted>(
                    writer.acceptRemote(remoteBundle(provider, registration).copyBytes(), RemoteTransportReceipt(null, false)),
                )
                assertEquals(DurableClockState(terminal, true), store.current.clockState)
                assertEquals(
                    LocalMutationFailure.HLC_EXHAUSTED,
                    assertIs<LocalMutationResult.Failure>(
                        writer.mutate(LocalSyncMutation.RemoveApplicationPolicy),
                    ).reason,
                )
            }
        }
    }

    @Test
    fun `given ambiguous terminal expiry commits when reconciled then only exact or proven absent outcomes succeed`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val sessionId = SessionId(app.posato.feature.sync.testIdentifier(95))
        val initial = acceptedSnapshot(
            provider,
            listOf(
                testOperation(1, 1, SyncOperationPayload.AuthorRegister),
                testOperation(2, 2, SyncOperationPayload.SessionStart(sessionId, 100, 200)),
            ),
        )
        val cases = listOf(
            LocalCommitMode.AMBIGUOUS_EXACT to true,
            LocalCommitMode.AMBIGUOUS_ABSENT_ONCE to true,
            LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE to false,
        )

        cases.forEach { (mode, expected) ->
            val store = FakeSyncReplicaStore(initial, expiryCommitMode = mode)
            val writer = assertIs<OpenSyncWriterResult.Success>(
                SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
            ).writer

            assertEquals(expected, writer.markTerminalExpiry(sessionId), mode.name)
        }
    }

    @Test
    fun `given a terminal local HLC when a valid remote registration arrives then it remains terminal and accepts inbound history`() {
        runTest {
            val provider = FakeSyncCryptoProvider()
            val terminal = HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, SyncFormatLimits.MAX_LOGICAL_COUNTER)
            val store = FakeSyncReplicaStore(snapshot(DurableClockState(terminal, true)))
            val writer = assertIs<OpenSyncWriterResult.Success>(
                SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
            ).writer

            val result = writer.acceptRemote(
                remoteBundle(provider, testOperation(1, 1, SyncOperationPayload.AuthorRegister)).copyBytes(),
                RemoteTransportReceipt(null, false),
            )

            assertIs<RemoteAcceptanceResult.Accepted>(result)
            assertEquals(DurableClockState(terminal, true), store.current.clockState)
        }
    }
}

private class FakeSyncReplicaStore(
    initial: SyncReplicaSnapshot,
    private val localCommitMode: LocalCommitMode = LocalCommitMode.SUCCESS,
    private val remoteCommitMode: RemoteCommitMode = RemoteCommitMode.SUCCESS,
    private val expiryCommitMode: LocalCommitMode = LocalCommitMode.SUCCESS,
    private val beforeOpen: suspend () -> Unit = {},
) : SyncReplicaStore {
    private var localCommitAttempts = 0
    private var expiryCommitAttempts = 0
    var current = initial
        private set

    override suspend fun open(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot> {
        beforeOpen()
        return SyncStoreResult.Success(current)
    }

    override suspend fun read(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot> {
        return SyncStoreResult.Success(current)
    }

    override suspend fun commitLocal(
        expectedRevision: Long,
        bundles: List<PreparedStoredBundle>,
        clockState: DurableClockState,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        localCommitAttempts += 1
        if (localCommitMode == LocalCommitMode.CANCELLED) {
            throw CancellationException("Synthetic local commit cancellation")
        }
        if (localCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE && localCommitAttempts == 1) {
            return SyncStoreResult.Failure(app.posato.feature.sync.data.SyncStoreFailure.AMBIGUOUS_RESULT)
        }
        val accepted = current.acceptedBundles.toMutableMap()
        val pending = current.pendingBundles.toMutableMap()
        bundles.forEach { prepared ->
            accepted[prepared.operation.operationId] = StoredAcceptedBundle(
                prepared.bundle,
                prepared.operationBytes,
                prepared.operation,
            )
            pending[prepared.operation.operationId] = prepared.bundle
        }
        current = current.copy(
            revision = current.revision + 1,
            clockState = clockState,
            acceptedBundles = accepted,
            pendingBundles = pending,
            transportProgress = if (localCommitMode == LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE) {
                OpaqueTransportProgress(byteArrayOf(99))
            } else {
                current.transportProgress
            },
        )

        return if (localCommitMode == LocalCommitMode.SUCCESS || localCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE) {
            SyncStoreResult.Success(current)
        } else {
            SyncStoreResult.Failure(app.posato.feature.sync.data.SyncStoreFailure.AMBIGUOUS_RESULT)
        }
    }

    override suspend fun commitAcceptedRemote(
        expectedRevision: Long,
        bundles: List<PreparedStoredBundle>,
        stagedBundleIdsToDelete: Set<BundleId>,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        val accepted = current.acceptedBundles.toMutableMap()
        bundles.forEach { prepared ->
            accepted[prepared.operation.operationId] = StoredAcceptedBundle(
                prepared.bundle,
                prepared.operationBytes,
                prepared.operation,
            )
        }
        current = current.copy(
            revision = current.revision + 1,
            clockState = clockState,
            acceptedBundles = accepted,
            stagedBundles = current.stagedBundles - stagedBundleIdsToDelete,
            transportProgress = transportProgress ?: current.transportProgress,
        )

        return remoteResult()
    }

    override suspend fun commitStagedRemote(
        expectedRevision: Long,
        bundle: PreparedStoredBundle,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        current = current.copy(
            revision = current.revision + 1,
            clockState = clockState,
            stagedBundles = current.stagedBundles + (
                bundle.operation.operationId to StoredStagedBundle(
                    bundle.bundle,
                    bundle.operationBytes,
                    bundle.operation,
                )
            ),
            transportProgress = transportProgress ?: current.transportProgress,
        )

        return remoteResult()
    }

    override suspend fun commitTransportProgress(
        expectedRevision: Long,
        transportProgress: OpaqueTransportProgress,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        current = current.copy(revision = current.revision + 1, transportProgress = transportProgress)

        return remoteResult()
    }

    override suspend fun markTerminalExpiry(
        expectedRevision: Long,
        sessionId: SessionId,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        expiryCommitAttempts += 1
        if (expiryCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE && expiryCommitAttempts == 1) {
            return SyncStoreResult.Failure(app.posato.feature.sync.data.SyncStoreFailure.AMBIGUOUS_RESULT)
        }
        current = current.copy(
            revision = current.revision + 1,
            terminalExpiryFacts = current.terminalExpiryFacts + sessionId,
            transportProgress = if (expiryCommitMode == LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE) {
                OpaqueTransportProgress(byteArrayOf(99))
            } else {
                current.transportProgress
            },
        )
        return if (expiryCommitMode == LocalCommitMode.SUCCESS || expiryCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE) {
            SyncStoreResult.Success(current)
        } else {
            SyncStoreResult.Failure(app.posato.feature.sync.data.SyncStoreFailure.AMBIGUOUS_RESULT)
        }
    }

    private fun remoteResult(): SyncStoreResult<SyncReplicaSnapshot> {
        return when (remoteCommitMode) {
            RemoteCommitMode.SUCCESS -> {
                SyncStoreResult.Success(current)
            }

            RemoteCommitMode.AMBIGUOUS_EXACT -> {
                SyncStoreResult.Failure(app.posato.feature.sync.data.SyncStoreFailure.AMBIGUOUS_RESULT)
            }

            RemoteCommitMode.AMBIGUOUS_WITH_EXTRA_STATE -> {
                current = current.copy(
                    terminalExpiryFacts = current.terminalExpiryFacts + SessionId(app.posato.feature.sync.testIdentifier(96)),
                )
                SyncStoreResult.Failure(app.posato.feature.sync.data.SyncStoreFailure.AMBIGUOUS_RESULT)
            }
        }
    }
}

private enum class LocalCommitMode {
    SUCCESS,
    CANCELLED,
    AMBIGUOUS_EXACT,
    AMBIGUOUS_ABSENT_ONCE,
    AMBIGUOUS_WITH_EXTRA_STATE,
}

private enum class RemoteCommitMode {
    SUCCESS,
    AMBIGUOUS_EXACT,
    AMBIGUOUS_WITH_EXTRA_STATE,
}

private fun snapshot(clockState: DurableClockState = DurableClockState(HybridLogicalClock(0, 0), false),): SyncReplicaSnapshot {
    return SyncReplicaSnapshot(
        context = testContext,
        revision = 0,
        clockState = clockState,
        acceptedBundles = emptyMap(),
        stagedBundles = emptyMap(),
        pendingBundles = emptyMap(),
        terminalExpiryFacts = emptySet(),
        transportProgress = null,
    )
}

private fun transportKey(): TransportKey {
    return checkNotNull(TransportKey.fromBytes(ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES) { 3 }))
}

private fun remoteBundle(
    provider: FakeSyncCryptoProvider,
    operation: SyncOperation,
): EncryptedBundle {
    val signingKey = checkNotNull(provider.createSigningKey())
    return try {
        val prepared = EncryptedBundleCodec(provider).prepare(
            operation,
            transportKey(),
            signingKey,
            ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { 4 },
        )
        assertIs<PrepareBundleResult.Success>(prepared).bundle
    } finally {
        signingKey.close()
    }
}

private fun stagedSnapshot(
    provider: FakeSyncCryptoProvider,
    count: Int,
    singleAuthor: Boolean,
): SyncReplicaSnapshot {
    val operations = (0 until count).map { index ->
        val author = if (singleAuthor) 10 else 100 + index
        val sequence = if (singleAuthor) index.toLong() + 2 else 2L
        testOperation(2_000 + index, sequence, SyncOperationPayload.ApplicationPolicyAbsent, author)
    }

    return snapshot().withStagedBundles(provider, operations)
}

private fun SyncReplicaSnapshot.withStagedBundles(
    provider: FakeSyncCryptoProvider,
    operations: List<SyncOperation>,
): SyncReplicaSnapshot {
    val staged = operations.map { operation -> storedStagedBundle(provider, operation) }
        .associateBy { stored -> stored.operation.operationId }

    return copy(stagedBundles = staged)
}

private fun storedStagedBundle(
    provider: FakeSyncCryptoProvider,
    operation: SyncOperation,
): StoredStagedBundle {
    return StoredStagedBundle(
        remoteBundle(provider, operation),
        ImmutableBytes(checkNotNull(app.posato.feature.sync.data.SyncOperationCodec.encode(operation))),
        operation,
    )
}

private fun acceptedSnapshot(
    provider: FakeSyncCryptoProvider,
    operations: List<SyncOperation>,
): SyncReplicaSnapshot {
    val lastClock = checkNotNull(operations.maxOfOrNull { operation -> operation.clock })
    val accepted = operations.map { operation -> storedAcceptedBundle(provider, operation) }
        .associateBy { stored -> stored.operation.operationId }
    return snapshot(DurableClockState(lastClock, lastClock.successor() == null)).copy(acceptedBundles = accepted)
}

private fun storedAcceptedBundle(
    provider: FakeSyncCryptoProvider,
    operation: SyncOperation,
): StoredAcceptedBundle {
    return StoredAcceptedBundle(
        remoteBundle(provider, operation),
        ImmutableBytes(checkNotNull(app.posato.feature.sync.data.SyncOperationCodec.encode(operation))),
        operation,
    )
}
