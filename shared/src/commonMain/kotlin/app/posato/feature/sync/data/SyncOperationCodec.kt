package app.posato.feature.sync.data

import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain

private val operationMagic = "PSO1".encodeToByteArray()
private const val OPERATION_FORMAT = 1
private const val AUTHOR_REGISTER_TAG = 1
private const val DOMAIN_PRESENT_TAG = 2
private const val DOMAIN_ABSENT_TAG = 3
private const val APPLICATION_POLICY_PRESENT_TAG = 4
private const val APPLICATION_POLICY_ABSENT_TAG = 5
private const val SESSION_START_TAG = 6
private const val SESSION_END_TAG = 7

internal object SyncOperationCodec {
    fun encode(operation: SyncOperation): ByteArray? {
        val writer = CanonicalWriter()
        writer.writeBytes(operationMagic)
        writer.writeU16(OPERATION_FORMAT)
        writer.writeBytes(operation.operationId.value.copyBytes())
        writer.writeBytes(operation.context.workspaceId.value.copyBytes())
        writer.writeBytes(operation.context.transportEpochId.value.copyBytes())
        writer.writeBytes(operation.context.keyEpochId.value.copyBytes())
        writer.writeBytes(operation.authorId.value.copyBytes())
        writer.writeBytes(operation.publicSigningKey.copyBytes())
        writer.writeLong(operation.authorSequence)
        writer.writeLong(operation.clock.physicalMillis)
        writer.writeU16(operation.clock.logicalCounter)
        if (!writer.writePayload(operation.payload)) {
            return null
        }
        val result = writer.toByteArray()

        return result.takeIf { bytes -> bytes.size <= SyncFormatLimits.PLAINTEXT_BYTES }
    }

    fun decode(bytes: ByteArray): SyncOperation? {
        val reader = bytes
            .takeIf { candidate -> candidate.size <= SyncFormatLimits.PLAINTEXT_BYTES }
            ?.let(::CanonicalReader)
            ?: return null
        val fields = reader.readOperationFields()
        val operation = fields?.toOperation()

        return operation?.takeIf { decoded -> reader.remaining == 0 && encode(decoded)?.contentEquals(bytes) == true }
    }
}

private data class DecodedOperationFields(
    val operationId: BundleId,
    val workspaceId: WorkspaceId,
    val transportEpochId: TransportEpochId,
    val keyEpochId: KeyEpochId,
    val authorId: AuthorId,
    val publicKey: PublicSigningKey,
    val authorSequence: Long,
    val physicalMillis: Long,
    val logicalCounter: Int,
    val payload: SyncOperationPayload,
) {
    override fun toString(): String {
        return "DecodedOperationFields(redacted)"
    }

    fun toOperation() = SyncOperation(
        operationId = operationId,
        context = SyncContext(workspaceId, transportEpochId, keyEpochId),
        authorId = authorId,
        publicSigningKey = publicKey,
        authorSequence = authorSequence,
        clock = HybridLogicalClock(physicalMillis, logicalCounter),
        payload = payload,
    )
}

private fun CanonicalReader.readOperationFields(): DecodedOperationFields? {
    val validMagic = readBytes(operationMagic.size)?.contentEquals(operationMagic) == true
    val validPrefix = validMagic && readU16() == OPERATION_FORMAT
    val operationId = readUuidIdentifier()?.let(::BundleId)
    val workspaceId = readUuidIdentifier()?.let(::WorkspaceId)
    val transportEpochId = readUuidIdentifier()?.let(::TransportEpochId)
    val keyEpochId = readUuidIdentifier()?.let(::KeyEpochId)
    val authorId = readUuidIdentifier()?.let(::AuthorId)
    val publicKey = readBytes(SyncFormatLimits.PUBLIC_KEY_BYTES)?.let(PublicSigningKey::fromBytes)
    val authorSequence = readLong()?.takeIf { value -> value > 0 }
    val physicalMillis = readLong()?.takeIf { value -> value in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS }
    val logicalCounter = readU16()
    val payload = readPayload()

    val hasContext = operationId != null && workspaceId != null && transportEpochId != null
    val hasIdentity = keyEpochId != null && authorId != null && publicKey != null
    val hasOperation = authorSequence != null && physicalMillis != null && logicalCounter != null
    val complete = listOf(validPrefix, hasContext, hasIdentity, hasOperation, payload != null).all { it }

    return if (complete) {
        DecodedOperationFields(
            checkNotNull(operationId),
            checkNotNull(workspaceId),
            checkNotNull(transportEpochId),
            checkNotNull(keyEpochId),
            checkNotNull(authorId),
            checkNotNull(publicKey),
            checkNotNull(authorSequence),
            checkNotNull(physicalMillis),
            checkNotNull(logicalCounter),
            checkNotNull(payload),
        )
    } else {
        null
    }
}

private fun CanonicalWriter.writePayload(payload: SyncOperationPayload): Boolean {
    when (payload) {
        SyncOperationPayload.AuthorRegister -> {
            writeByte(AUTHOR_REGISTER_TAG)
        }

        is SyncOperationPayload.DomainPresent -> {
            writeByte(DOMAIN_PRESENT_TAG)
            writeString(payload.domain.canonicalValue)
        }

        is SyncOperationPayload.DomainAbsent -> {
            writeByte(DOMAIN_ABSENT_TAG)
            writeString(payload.domain.canonicalValue)
        }

        is SyncOperationPayload.ApplicationPolicyPresent -> {
            writeByte(APPLICATION_POLICY_PRESENT_TAG)
            writeBytes(SyncIdentifier.zero().copyBytes())
            writeString(payload.name.canonicalValue)
        }

        SyncOperationPayload.ApplicationPolicyAbsent -> {
            writeByte(APPLICATION_POLICY_ABSENT_TAG)
            writeBytes(SyncIdentifier.zero().copyBytes())
        }

        is SyncOperationPayload.SessionStart -> {
            if (!payload.hasValidBounds()) {
                return false
            }
            writeByte(SESSION_START_TAG)
            writeBytes(payload.sessionId.value.copyBytes())
            writeLong(payload.startEpochMillis)
            writeLong(payload.mandatoryEndEpochMillis)
        }

        is SyncOperationPayload.SessionEnd -> {
            writeByte(SESSION_END_TAG)
            writeBytes(payload.sessionId.value.copyBytes())
        }
    }

    return true
}

private fun CanonicalWriter.writeString(value: String) {
    val bytes = value.encodeToByteArray(throwOnInvalidSequence = true)
    writeU16(bytes.size)
    writeBytes(bytes)
}

private fun CanonicalReader.readPayload(): SyncOperationPayload? {
    return when (readByte()) {
        AUTHOR_REGISTER_TAG -> SyncOperationPayload.AuthorRegister
        DOMAIN_PRESENT_TAG -> readCanonicalDomain()?.let(SyncOperationPayload::DomainPresent)
        DOMAIN_ABSENT_TAG -> readCanonicalDomain()?.let(SyncOperationPayload::DomainAbsent)
        APPLICATION_POLICY_PRESENT_TAG -> readApplicationPolicyPresent()
        APPLICATION_POLICY_ABSENT_TAG -> readSingletonIdentifier()?.let { SyncOperationPayload.ApplicationPolicyAbsent }
        SESSION_START_TAG -> readSessionStart()
        SESSION_END_TAG -> readUuidIdentifier()?.let(::SessionId)?.let(SyncOperationPayload::SessionEnd)
        else -> null
    }
}

private fun CanonicalReader.readCanonicalDomain(): ExactDomain? {
    return readCanonicalString()
        ?.takeIf { value -> value.all { character -> character.code <= MAX_ASCII_CODE_POINT } }
        ?.let(ExactDomain::restore)
}

private fun CanonicalReader.readApplicationPolicyPresent(): SyncOperationPayload.ApplicationPolicyPresent? {
    val singleton = readSingletonIdentifier()
    val name = readCanonicalString()?.let(ApplicationPolicyName::restore)

    return name?.takeIf { singleton != null }?.let(SyncOperationPayload::ApplicationPolicyPresent)
}

private fun CanonicalReader.readSessionStart(): SyncOperationPayload.SessionStart? {
    val sessionId = readUuidIdentifier()?.let(::SessionId)
    val startEpochMillis = readLong()
    val mandatoryEndEpochMillis = readLong()
    val payload = if (sessionId != null && startEpochMillis != null && mandatoryEndEpochMillis != null) {
        SyncOperationPayload.SessionStart(sessionId, startEpochMillis, mandatoryEndEpochMillis)
    } else {
        null
    }

    return payload?.takeIf(SyncOperationPayload.SessionStart::hasValidBounds)
}

private fun CanonicalReader.readCanonicalString(): String? {
    val bytes = readU16()?.let(::readBytes)

    return try {
        bytes?.decodeToString(throwOnInvalidSequence = true)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private const val MAX_ASCII_CODE_POINT = 0x7F

private fun CanonicalReader.readUuidIdentifier(): SyncIdentifier? {
    return readBytes(SyncFormatLimits.IDENTIFIER_BYTES)?.let(SyncIdentifier::fromUuidV4Bytes)
}

private fun CanonicalReader.readSingletonIdentifier(): SyncIdentifier? {
    return readBytes(SyncFormatLimits.IDENTIFIER_BYTES)
        ?.let(SyncIdentifier::fromExactBytes)
        ?.takeIf(SyncIdentifier::isZero)
}

private fun SyncOperationPayload.SessionStart.hasValidBounds(): Boolean {
    val validInstants = startEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS &&
        mandatoryEndEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS
    val duration = mandatoryEndEpochMillis - startEpochMillis

    return validInstants && duration in 1..SyncFormatLimits.MAX_SESSION_DURATION_MILLIS
}
