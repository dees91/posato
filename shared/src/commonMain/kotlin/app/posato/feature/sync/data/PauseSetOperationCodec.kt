package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.targets.domain.ExactDomain

private const val SET_PUT_TAG = 12
private const val SET_REMOVE_TAG = 13
private const val SET_DOMAIN_PRESENT_TAG = 14
private const val SET_DOMAIN_ABSENT_TAG = 15
private const val SET_DEFAULT_TAG = 16
private const val SCHEDULE_SET_PUT_TAG = 17
private const val SESSION_SET_START_TAG = 18
private const val PAUSE_SETS_ENABLED_TAG = 19

internal val PAUSE_SET_TAGS: IntRange = SET_PUT_TAG..PAUSE_SETS_ENABLED_TAG

/** Whether the payload travels as one of kinds 12-19: a set operation, or a domain, schedule or session change naming its set. */
internal fun SyncOperationPayload.isPauseSetKind(): Boolean {
    return when (this) {
        is SyncOperationPayload.PauseSetPut,
        is SyncOperationPayload.PauseSetRemove,
        is SyncOperationPayload.PauseSetDefault,
        SyncOperationPayload.PauseSetsEnabled -> true

        is SyncOperationPayload.DomainPresent -> setId != null

        is SyncOperationPayload.DomainAbsent -> setId != null

        is SyncOperationPayload.SchedulePut -> setId != null

        is SyncOperationPayload.SessionStart -> setId != null

        else -> false
    }
}

/** Kinds 12-19 in the ADR 0006 pause set amendment's layout. Returns false for a payload the rules refuse. */
internal fun CanonicalWriter.writePauseSetPayload(payload: SyncOperationPayload): Boolean {
    return when (payload) {
        is SyncOperationPayload.PauseSetPut -> {
            writeSetPut(payload)
        }

        is SyncOperationPayload.PauseSetRemove -> {
            writeSetTag(SET_REMOVE_TAG, payload.setId)
        }

        is SyncOperationPayload.PauseSetDefault -> {
            writeSetTag(SET_DEFAULT_TAG, payload.setId)
        }

        SyncOperationPayload.PauseSetsEnabled -> {
            writeByte(PAUSE_SETS_ENABLED_TAG)
            true
        }

        is SyncOperationPayload.DomainPresent -> {
            writeSetDomain(SET_DOMAIN_PRESENT_TAG, payload.setId, payload.domain.canonicalValue)
        }

        is SyncOperationPayload.DomainAbsent -> {
            writeSetDomain(SET_DOMAIN_ABSENT_TAG, payload.setId, payload.domain.canonicalValue)
        }

        is SyncOperationPayload.SchedulePut -> {
            writeTrailingSet(SCHEDULE_SET_PUT_TAG, payload.setId.takeIf { ScheduleWireRules.isValid(payload) }) {
                writeSchedulePutBody(payload)
            }
        }

        is SyncOperationPayload.SessionStart -> {
            writeTrailingSet(SESSION_SET_START_TAG, payload.setId.takeIf { payload.hasValidBounds() }) {
                writeOwnedBytes(payload.sessionId.value.copyBytes())
                writeLong(payload.startEpochMillis)
                writeLong(payload.mandatoryEndEpochMillis)
            }
        }

        else -> {
            false
        }
    }
}

internal fun CanonicalReader.readPauseSetPayload(tag: Int): SyncOperationPayload? {
    return when (tag) {
        SET_PUT_TAG -> readSetPut()
        SET_REMOVE_TAG -> readPauseSetId()?.let(SyncOperationPayload::PauseSetRemove)
        SET_DOMAIN_PRESENT_TAG -> readSetDomain { setId, domain -> SyncOperationPayload.DomainPresent(domain, setId) }
        SET_DOMAIN_ABSENT_TAG -> readSetDomain { setId, domain -> SyncOperationPayload.DomainAbsent(domain, setId) }
        SET_DEFAULT_TAG -> readPauseSetId()?.let(SyncOperationPayload::PauseSetDefault)
        SCHEDULE_SET_PUT_TAG -> readTrailingSet(readSchedulePut()) { put, setId -> put.copy(setId = setId) }
        SESSION_SET_START_TAG -> readTrailingSet(readSessionStart()) { start, setId -> start.copy(setId = setId) }
        PAUSE_SETS_ENABLED_TAG -> SyncOperationPayload.PauseSetsEnabled
        else -> null
    }
}

private fun CanonicalWriter.writeSetPut(payload: SyncOperationPayload.PauseSetPut): Boolean {
    if (!ScheduleWireRules.isValidName(payload.name)) {
        return false
    }
    writeByte(SET_PUT_TAG)
    writeOwnedBytes(payload.setId.value.copyBytes())
    writeString(payload.name)
    return true
}

private fun CanonicalWriter.writeSetTag(
    tag: Int,
    setId: PauseSetId,
): Boolean {
    writeByte(tag)
    writeOwnedBytes(setId.value.copyBytes())
    return true
}

private fun CanonicalWriter.writeSetDomain(
    tag: Int,
    setId: PauseSetId?,
    domain: String,
): Boolean {
    if (setId == null) {
        return false
    }
    writeSetTag(tag, setId)
    writeString(domain)
    return true
}

/** Kinds 17 and 18: the complete kind 8 or kind 6 payload, then the set; a null set means the payload is refused. */
private fun CanonicalWriter.writeTrailingSet(
    tag: Int,
    setId: PauseSetId?,
    writeBody: CanonicalWriter.() -> Unit,
): Boolean {
    if (setId == null) {
        return false
    }
    writeByte(tag)
    writeBody()
    writeOwnedBytes(setId.value.copyBytes())
    return true
}

private fun CanonicalReader.readPauseSetId(): PauseSetId? {
    return readBytes(SyncFormatLimits.IDENTIFIER_BYTES)
        ?.let(SyncIdentifier::fromExactBytes)
        ?.let(PauseSetId::of)
}

private fun CanonicalReader.readSetPut(): SyncOperationPayload.PauseSetPut? {
    val setId = readPauseSetId()
    val name = readCanonicalString()?.takeIf(ScheduleWireRules::isValidName)

    return if (setId != null && name != null) SyncOperationPayload.PauseSetPut(setId, name) else null
}

private fun CanonicalReader.readSetDomain(build: (PauseSetId, ExactDomain) -> SyncOperationPayload): SyncOperationPayload? {
    val setId = readPauseSetId()
    val domain = readCanonicalDomain()

    return if (setId != null && domain != null) build(setId, domain) else null
}

private fun <T : SyncOperationPayload> CanonicalReader.readTrailingSet(
    body: T?,
    attach: (T, PauseSetId) -> T,
): T? {
    val setId = readPauseSetId()

    return if (body != null && setId != null) attach(body, setId) else null
}
