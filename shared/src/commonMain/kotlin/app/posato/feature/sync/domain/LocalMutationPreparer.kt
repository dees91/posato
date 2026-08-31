package app.posato.feature.sync.domain

import app.posato.feature.sync.data.DurableClockState
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.PrepareBundleResult
import app.posato.feature.sync.data.PreparedStoredBundle
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.SyncSigningKey

internal class LocalMutationPreparer(
    private val cryptoProvider: SyncCryptoProvider,
    private val codec: EncryptedBundleCodec,
    private val transportKey: TransportKey,
) {
    fun prepare(
        mutation: LocalSyncMutation,
        clocks: List<HybridLogicalClock>,
        existing: AuthoringIncarnation?,
        context: SyncContext,
    ): PreparedLocalMutation? {
        val signingKey = existing?.signingKey ?: cryptoProvider.createSigningKey()
        val authorId = existing?.authorId ?: createUuidV4()?.let(::AuthorId)
        val payload = mutation.toPayload()
        val operations = createOperations(existing, authorId, signingKey, clocks, payload, context)
        val bundles = operations?.let { prepareBundles(it, signingKey) }
        val complete = listOf(authorId, signingKey, operations, bundles).all { it != null }
        val prepared = if (complete) {
            PreparedLocalMutation(
                checkNotNull(bundles),
                DurableClockState(clocks.last(), clocks.last().successor() == null),
                AuthoringIncarnation(
                    checkNotNull(authorId),
                    checkNotNull(signingKey),
                    nextAuthorSequence(checkNotNull(operations).last().authorSequence),
                ),
            )
        } else {
            null
        }
        if (prepared == null && existing == null) signingKey?.close()
        return prepared
    }

    private fun createOperations(
        existing: AuthoringIncarnation?,
        authorId: AuthorId?,
        signingKey: SyncSigningKey?,
        clocks: List<HybridLogicalClock>,
        payload: SyncOperationPayload?,
        context: SyncContext,
    ): List<SyncOperation>? {
        if (authorId == null || signingKey == null || payload == null) return null
        val specifications = if (existing == null) {
            listOf(Triple(1L, clocks[0], SyncOperationPayload.AuthorRegister), Triple(2L, clocks[1], payload))
        } else {
            listOf(Triple(checkNotNull(existing.nextSequence), clocks.single(), payload))
        }
        return specifications.map { (sequence, clock, operationPayload) ->
            createOperation(authorId, signingKey, sequence, clock, operationPayload, context)
        }.takeIf { operations -> operations.all { it != null } }?.filterNotNull()
    }

    private fun createOperation(
        authorId: AuthorId,
        signingKey: SyncSigningKey,
        sequence: Long,
        clock: HybridLogicalClock,
        payload: SyncOperationPayload,
        context: SyncContext,
    ): SyncOperation? = createUuidV4()?.let { identifier ->
        SyncOperation(BundleId(identifier), context, authorId, signingKey.publicKey, sequence, clock, payload)
    }

    private fun prepareBundles(
        operations: List<SyncOperation>,
        signingKey: SyncSigningKey?
    ): List<PreparedStoredBundle>? {
        val bundles = operations.map { operation ->
            val salt = cryptoProvider.randomBytes(SyncFormatLimits.BUNDLE_SALT_BYTES)
            val result = salt?.let { codec.prepare(operation, transportKey, checkNotNull(signingKey), it) }
            (result as? PrepareBundleResult.Success)?.let { PreparedStoredBundle(it.bundle, operation) }
        }
        return bundles.takeIf { prepared -> prepared.all { it != null } }?.filterNotNull()
    }

    private fun createUuidV4(): SyncIdentifier? {
        val bytes = cryptoProvider.randomBytes(SyncFormatLimits.IDENTIFIER_BYTES) ?: return null
        bytes[UUID_VERSION_INDEX] = (bytes[UUID_VERSION_INDEX].toInt() and UUID_VERSION_CLEAR_MASK or UUID_VERSION_FOUR).toByte()
        bytes[UUID_VARIANT_INDEX] = (bytes[UUID_VARIANT_INDEX].toInt() and UUID_VARIANT_CLEAR_MASK or UUID_RFC_VARIANT).toByte()
        return SyncIdentifier.fromUuidV4Bytes(bytes)
    }
}

private const val UUID_VERSION_INDEX = 6
private const val UUID_VARIANT_INDEX = 8
private const val UUID_VERSION_CLEAR_MASK = 0x0F
private const val UUID_VERSION_FOUR = 0x40
private const val UUID_VARIANT_CLEAR_MASK = 0x3F
private const val UUID_RFC_VARIANT = 0x80
