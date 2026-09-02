package app.posato.feature.sync.domain

import app.posato.feature.sync.data.DecodeBundleResult
import app.posato.feature.sync.data.DurableClockState
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.StoredAcceptedBundle
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.SyncOperationCodec
import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.data.useAndClear

internal fun SyncReplicaSnapshot.isAuthenticatedBy(
    cryptoProvider: SyncCryptoProvider,
    transportKey: TransportKey,
): Boolean {
    val codec = EncryptedBundleCodec(cryptoProvider)

    return acceptedBundlesAreValid(codec, transportKey) &&
        acceptedBundles.values.haveValidAuthorHistories() &&
        hasClockStateWithinConservativeReachabilityBounds() &&
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
            SyncOperationCodec.encode(stored.operation)
                ?.useAndClear(stored.operationBytes::contentEquals) == true
    }
}

private fun SyncReplicaSnapshot.hasClockStateWithinConservativeReachabilityBounds(): Boolean {
    val initialClock = HybridLogicalClock(0, 0)
    val terminalClock = HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, SyncFormatLimits.MAX_LOGICAL_COUNTER)
    val operations = acceptedBundles.values.map(StoredAcceptedBundle::operation)
    val hasConsistentExhaustion = (clockState.last == terminalClock) == clockState.isExhausted
    val isWithinReachabilityBounds = if (operations.isEmpty()) {
        clockState == DurableClockState(initialClock, false)
    } else {
        val lowerClock = operations.maxOf(SyncOperation::clock)
        val remoteUpperState = advanceRemoteClock(
            DurableClockState(lowerClock, lowerClock == terminalClock),
            operations,
        )
        val canExhaustWithoutAnotherOperation = reserveLocalClocks(
            remoteUpperState,
            remoteUpperState.last.physicalMillis,
            2,
        ) == null
        val upperClock = if (canExhaustWithoutAnotherOperation) terminalClock else remoteUpperState.last

        clockState.last != initialClock && clockState.last in lowerClock..upperClock
    }

    return hasConsistentExhaustion && isWithinReachabilityBounds
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
            SyncOperationCodec.encode(stored.operation)
                ?.useAndClear(stored.operationBytes::contentEquals) == true
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
