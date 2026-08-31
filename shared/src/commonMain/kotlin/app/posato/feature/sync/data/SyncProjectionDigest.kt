package app.posato.feature.sync.data

import app.posato.feature.sync.domain.SyncAuditOutcome
import app.posato.feature.sync.domain.SyncProjection

internal fun SyncProjection.canonicalDigest(cryptoProvider: SyncCryptoProvider): ImmutableBytes? {
    val writer = CanonicalWriter()
    writer.writeBytes("PSP1".encodeToByteArray())
    writer.writeU32(domains.size.toLong())
    domains.forEach { domain -> writer.writeCanonicalString(domain.canonicalValue) }
    if (applicationPolicyName == null) {
        writer.writeByte(0)
    } else {
        writer.writeByte(1)
        writer.writeCanonicalString(applicationPolicyName.canonicalValue)
    }
    writer.writeU32(eligibleSessionStarts.size.toLong())
    eligibleSessionStarts.forEach { start ->
        writer.writeBytes(start.operationId.value.copyBytes())
        writer.writeBytes(start.sessionId.value.copyBytes())
        writer.writeLong(start.startEpochMillis)
        writer.writeLong(start.mandatoryEndEpochMillis)
        writer.writeByte(if (start.isEnded) 1 else 0)
    }
    writer.writeU32(conflictedSessionIds.size.toLong())
    conflictedSessionIds.sortedBy { sessionId -> sessionId.value }.forEach { sessionId ->
        writer.writeBytes(sessionId.value.copyBytes())
    }
    writer.writeU32(audit.size.toLong())
    audit.forEach { entry ->
        writer.writeBytes(entry.operationId.value.copyBytes())
        writer.writeByte(entry.outcome.formatTag())
    }

    return cryptoProvider.sha256(writer.toByteArray())?.let(::ImmutableBytes)
}

private fun CanonicalWriter.writeCanonicalString(value: String) {
    val bytes = value.encodeToByteArray(throwOnInvalidSequence = true)
    writeU16(bytes.size)
    writeBytes(bytes)
}

private fun SyncAuditOutcome.formatTag(): Int {
    return when (this) {
        SyncAuditOutcome.AUTHOR_REGISTERED -> AUTHOR_REGISTERED_TAG
        SyncAuditOutcome.APPLIED -> APPLIED_TAG
        SyncAuditOutcome.NO_OP -> NO_OP_TAG
        SyncAuditOutcome.DOMAIN_CAPACITY -> DOMAIN_CAPACITY_TAG
        SyncAuditOutcome.SEQUENCE_GAP -> SEQUENCE_GAP_TAG
        SyncAuditOutcome.SESSION_CONFLICT -> SESSION_CONFLICT_TAG
    }
}

private const val AUTHOR_REGISTERED_TAG = 1
private const val APPLIED_TAG = 2
private const val NO_OP_TAG = 3
private const val DOMAIN_CAPACITY_TAG = 4
private const val SEQUENCE_GAP_TAG = 5
private const val SESSION_CONFLICT_TAG = 6
