package app.posato.feature.sync.domain

import app.posato.feature.sync.data.DecodeBundleResult
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.StoredAcceptedBundle
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.SyncOperationCodec
import app.posato.feature.sync.data.SyncReplicaSnapshot

internal fun SyncReplicaSnapshot.isAuthenticatedBy(
    cryptoProvider: SyncCryptoProvider,
    transportKey: TransportKey,
): Boolean {
    val initialClock = HybridLogicalClock(0, 0)
    val terminalClock = HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, SyncFormatLimits.MAX_LOGICAL_COUNTER)
    val codec = EncryptedBundleCodec(cryptoProvider)

    return (clockState.last == terminalClock) == clockState.isExhausted &&
        (acceptedBundles.isEmpty() || clockState.last != initialClock) &&
        acceptedBundlesAreValid(codec, transportKey) &&
        acceptedBundles.values.haveValidAuthorHistories() &&
        hasValidStagingState() &&
        stagedBundlesAreValid(codec, transportKey) &&
        hasValidTerminalExpiryFacts()
}

internal fun SyncReplicaSnapshot.retainedNonGapSessionIds(projection: SyncProjection): Set<SessionId> {
    val nonGapOperationIds = projection.audit
        .filterNot { entry -> entry.outcome == SyncAuditOutcome.SEQUENCE_GAP }
        .mapTo(mutableSetOf(), SyncAuditEntry::operationId)

    return acceptedBundles.values.mapNotNullTo(mutableSetOf()) { stored ->
        (stored.operation.payload as? SyncOperationPayload.SessionStart)
            ?.takeIf { stored.operation.operationId in nonGapOperationIds }
            ?.sessionId
    }
}

private fun SyncReplicaSnapshot.acceptedBundlesAreValid(
    codec: EncryptedBundleCodec,
    transportKey: TransportKey,
): Boolean {
    return acceptedBundles.all { (bundleId, stored) ->
        val decoded = codec.decode(stored.bundle, context, transportKey)
        decoded is DecodeBundleResult.Success &&
            bundleId == stored.operation.operationId &&
            decoded.operation == stored.operation &&
            stored.operation.clock <= clockState.last &&
            stored.operationBytes.copyBytes().contentEquals(SyncOperationCodec.encode(stored.operation))
    }
}

private fun SyncReplicaSnapshot.stagedBundlesAreValid(
    codec: EncryptedBundleCodec,
    transportKey: TransportKey,
): Boolean {
    return stagedBundles.all { (bundleId, stored) ->
        val decoded = codec.decode(stored.bundle, context, transportKey)
        decoded is DecodeBundleResult.Success &&
            bundleId == stored.operation.operationId &&
            decoded.operation == stored.operation &&
            stored.operation.authorSequence > 1L &&
            stored.operation.payload != SyncOperationPayload.AuthorRegister &&
            stored.operationBytes.copyBytes().contentEquals(SyncOperationCodec.encode(stored.operation))
    }
}

private fun SyncReplicaSnapshot.hasValidStagingState(): Boolean {
    val acceptedAuthorIds = acceptedBundles.values.mapTo(mutableSetOf()) { stored -> stored.operation.authorId }
    val operationsByAuthor = stagedBundles.values.map { stored -> stored.operation }.groupBy(SyncOperation::authorId)

    return stagedBundles.size <= SyncFormatLimits.MAX_STAGED_BUNDLES &&
        stagedBundles.keys.none(acceptedBundles::containsKey) &&
        operationsByAuthor.keys.none(acceptedAuthorIds::contains) &&
        operationsByAuthor.values.all { operations ->
            val sequences = operations.map(SyncOperation::authorSequence)
            operations.size <= SyncFormatLimits.MAX_UNKNOWN_AUTHOR_BUNDLES &&
                sequences.distinct().size == sequences.size
        }
}

private fun SyncReplicaSnapshot.hasValidTerminalExpiryFacts(): Boolean {
    val projection = SyncReducer.reduce(acceptedBundles.values.map(StoredAcceptedBundle::operation))
    val retainedSessionIds = retainedNonGapSessionIds(projection)

    return terminalExpiryFacts.all(retainedSessionIds::contains)
}

private fun Collection<StoredAcceptedBundle>.haveValidAuthorHistories(): Boolean {
    return map(StoredAcceptedBundle::operation).groupBy(SyncOperation::authorId).values.all { operations ->
        val registration = operations.singleOrNull { operation -> operation.authorSequence == 1L }
            ?: return@all false
        val sequences = operations.map(SyncOperation::authorSequence)

        registration.payload == SyncOperationPayload.AuthorRegister &&
            sequences.distinct().size == sequences.size &&
            operations.all { operation ->
                operation.publicSigningKey == registration.publicSigningKey &&
                    (operation.payload != SyncOperationPayload.AuthorRegister || operation.authorSequence == 1L)
            }
    }
}
