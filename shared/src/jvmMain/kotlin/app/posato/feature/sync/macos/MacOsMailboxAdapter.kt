package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.ZoneDeleteResult
import kotlinx.coroutines.CancellationException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class MacOsMailboxAdapter(
    private val transport: SyncCompanionTransport,
    private val deadlineMilliseconds: Int = DEFAULT_DEADLINE_MILLISECONDS,
) {
    suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray,
    ): BundleSaveResult {
        val binding = expectedBinding.copyBytes()
        val identifierCopy = identifier.copyOf()
        val payloadCopy = payload.copyOf()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.SaveBundle,
                payload = MacOsSyncCompanionProtocol.bundlePayload(binding, identifierCopy, payloadCopy),
            )
            when (response) {
                CompanionExchange.Unknown -> BundleSaveResult.UnknownOutcome
                is CompanionExchange.Message -> mapBundleSave(response.message)
            }
        } finally {
            binding.fill(0)
            identifierCopy.fill(0)
            payloadCopy.fill(0)
        }
    }

    suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor,
    ): ChangeFetchResult {
        val binding = expectedBinding.copyBytes()
        val cursorBytes = cursor.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.FetchChanges,
                payload = MacOsSyncCompanionProtocol.cursorPayload(binding, cursorBytes),
            )
            when (response) {
                CompanionExchange.Unknown -> ChangeFetchResult.UnknownOutcome
                is CompanionExchange.Message -> mapFetchChanges(response.message)
            }
        } finally {
            binding.fill(0)
            cursorBytes.fill(0)
        }
    }

    suspend fun deleteZoneAndVerifyAbsent(expectedBinding: AccountBinding): ZoneDeleteResult {
        val binding = expectedBinding.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.DeleteZoneAndVerifyAbsent,
                payload = MacOsSyncCompanionProtocol.cloudPayload(binding),
            )
            when (response) {
                CompanionExchange.Unknown -> ZoneDeleteResult.UnknownOutcome
                is CompanionExchange.Message -> mapZoneDelete(response.message)
            }
        } finally {
            binding.fill(0)
        }
    }

    private suspend fun exchange(
        operation: SyncCompanionOperation,
        payload: ByteArray,
    ): CompanionExchange {
        val message = SyncCompanionMessage(
            operation = operation,
            requestIdentifier = transport.newRequestIdentifier(),
            deadlineMilliseconds = deadlineMilliseconds,
            capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
            outcome = null,
            payload = payload,
        )
        return try {
            transport.transact(message)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            CompanionExchange.Unknown
        } catch (_: IllegalStateException) {
            CompanionExchange.Unknown
        } finally {
            payload.fill(0)
        }
    }

    private fun mapBundleSave(message: SyncCompanionMessage): BundleSaveResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Created -> {
                BundleSaveResult.Saved
            }

            SyncCompanionOutcome.Identical -> {
                BundleSaveResult.Identical
            }

            SyncCompanionOutcome.Conflict -> {
                BundleSaveResult.Conflict
            }

            SyncCompanionOutcome.Retryable -> {
                BundleSaveResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                BundleSaveResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                BundleSaveResult.UnknownOutcome
            }

            SyncCompanionOutcome.IntegrityFailure -> {
                BundleSaveResult.IntegrityFailure
            }

            SyncCompanionOutcome.Found,
            SyncCompanionOutcome.Missing,
            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            null -> {
                BundleSaveResult.UnknownOutcome
            }
        }
    }

    private fun mapFetchChanges(message: SyncCompanionMessage): ChangeFetchResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Found -> {
                val page = parsePage(message.payload)
                if (page == null) {
                    ChangeFetchResult.IntegrityFailure
                } else {
                    ChangeFetchResult.Page(page)
                }
            }

            SyncCompanionOutcome.Missing -> {
                ChangeFetchResult.ZoneMissing
            }

            SyncCompanionOutcome.Retryable -> {
                ChangeFetchResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                ChangeFetchResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                ChangeFetchResult.UnknownOutcome
            }

            SyncCompanionOutcome.IntegrityFailure -> {
                ChangeFetchResult.IntegrityFailure
            }

            SyncCompanionOutcome.Created,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            null -> {
                ChangeFetchResult.UnknownOutcome
            }
        }
    }

    private fun mapZoneDelete(message: SyncCompanionMessage): ZoneDeleteResult {
        return when (message.outcome) {
            SyncCompanionOutcome.DeletedAndAbsent -> {
                ZoneDeleteResult.DeletedAndAbsent
            }

            SyncCompanionOutcome.Retryable -> {
                ZoneDeleteResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                ZoneDeleteResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                ZoneDeleteResult.UnknownOutcome
            }

            SyncCompanionOutcome.Found,
            SyncCompanionOutcome.Missing,
            SyncCompanionOutcome.Created,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.IntegrityFailure,
            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            null -> {
                ZoneDeleteResult.UnknownOutcome
            }
        }
    }

    private fun parsePage(payload: ByteArray): ChangePage? {
        return try {
            readPage(payload)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: BufferUnderflowException) {
            null
        }
    }

    private fun readPage(payload: ByteArray): ChangePage {
        require(payload.size >= MINIMUM_PAGE_BYTES)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val more = buffer.get()
        require(more == ABSENT_FLAG || more == PRESENT_FLAG)
        val cursorLength = buffer.int
        require(cursorLength in 0..MacOsSyncCompanionProtocol.CURSOR_BYTES)
        require(buffer.remaining() >= cursorLength + MINIMUM_TAIL_BYTES)
        val cursor = ByteArray(cursorLength).also(buffer::get)
        val present = buffer.get()
        require(present == ABSENT_FLAG || present == PRESENT_FLAG)
        val bundle = if (present == PRESENT_FLAG) readBundled(buffer) else null
        if (present != PRESENT_FLAG) {
            require(buffer.remaining() == 0)
        }
        val cursorValue = requireNotNull(MailboxCursor.fromBytes(cursor))
        return ChangePage(
            bundle = bundle,
            moreChanges = more == PRESENT_FLAG,
            nextCursor = cursorValue,
        )
    }

    private fun readBundled(buffer: ByteBuffer): MailboxBundle {
        require(buffer.remaining() >= MacOsSyncCompanionProtocol.BUNDLE_IDENTIFIER_BYTES + LENGTH_BYTES)
        val identifier = ByteArray(MacOsSyncCompanionProtocol.BUNDLE_IDENTIFIER_BYTES).also(buffer::get)
        val bundleLength = buffer.int
        require(bundleLength in 1..MacOsSyncCompanionProtocol.BUNDLE_BYTES)
        require(buffer.remaining() == bundleLength)
        val bytes = ByteArray(bundleLength).also(buffer::get)
        return requireNotNull(MailboxBundle.fromParts(identifier, bytes))
    }

    private companion object {
        const val DEFAULT_DEADLINE_MILLISECONDS: Int = 30_000
        const val ABSENT_FLAG: Byte = 0
        const val PRESENT_FLAG: Byte = 1
        const val LENGTH_BYTES: Int = 4
        const val MINIMUM_PAGE_BYTES: Int = 6
        const val MINIMUM_TAIL_BYTES: Int = 1
    }
}
