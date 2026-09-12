package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.RecordDeleteResult
import kotlinx.coroutines.CancellationException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class MacOsMailboxAdapter(
    private val transport: SyncCompanionTransport,
    private val deadlineMilliseconds: Int = DEFAULT_DEADLINE_MILLISECONDS,
) : MailboxPort {
    override suspend fun saveBundle(
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

    override suspend fun fetchChanges(
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

    override suspend fun deleteWorkspaceRecords(expectedBinding: AccountBinding): RecordDeleteResult {
        val binding = expectedBinding.copyBytes()
        var cursor: ByteArray? = null
        return try {
            repeat(MAX_DELETE_ATTEMPTS) {
                val payload = if (cursor == null) {
                    MacOsSyncCompanionProtocol.cloudPayload(binding)
                } else {
                    MacOsSyncCompanionProtocol.cursorPayload(binding, checkNotNull(cursor))
                }
                val response = exchange(
                    operation = SyncCompanionOperation.DeleteWorkspaceRecords,
                    payload = payload,
                )
                when (response) {
                    CompanionExchange.Unknown -> {
                        // The companion died mid-request; resume from the last cursor.
                    }

                    is CompanionExchange.Message -> {
                        if (response.message.outcome == SyncCompanionOutcome.Incomplete) {
                            cursor = parseResumeCursor(response.message.payload)
                                ?: return RecordDeleteResult.UnknownOutcome
                        } else {
                            return mapRecordDelete(response.message)
                        }
                    }
                }
            }
            RecordDeleteResult.Retryable
        } finally {
            binding.fill(0)
            cursor?.fill(0)
        }
    }

    override suspend fun sweepBundlesIfAnchorMissing(expectedBinding: AccountBinding): BundleSweepResult {
        val binding = expectedBinding.copyBytes()
        var cursor: ByteArray? = null
        return try {
            repeat(MAX_DELETE_ATTEMPTS) {
                val payload = if (cursor == null) {
                    MacOsSyncCompanionProtocol.cloudPayload(binding)
                } else {
                    MacOsSyncCompanionProtocol.cursorPayload(binding, checkNotNull(cursor))
                }
                val response = exchange(
                    operation = SyncCompanionOperation.SweepBundlesIfAnchorMissing,
                    payload = payload,
                )
                when (response) {
                    CompanionExchange.Unknown -> {
                        // The companion died mid-request; resume from the last cursor.
                    }

                    is CompanionExchange.Message -> {
                        if (response.message.outcome == SyncCompanionOutcome.Incomplete) {
                            cursor = parseResumeCursor(response.message.payload)
                                ?: return BundleSweepResult.UnknownOutcome
                        } else {
                            return when (response.message.outcome) {
                                SyncCompanionOutcome.Swept -> BundleSweepResult.Swept
                                SyncCompanionOutcome.AnchorPresent -> BundleSweepResult.AnchorPresent
                                SyncCompanionOutcome.Retryable -> BundleSweepResult.Retryable
                                SyncCompanionOutcome.AccountChanged -> BundleSweepResult.AccountChanged
                                else -> BundleSweepResult.UnknownOutcome
                            }
                        }
                    }
                }
            }
            BundleSweepResult.Retryable
        } finally {
            binding.fill(0)
            cursor?.fill(0)
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
            SyncCompanionOutcome.TokenExpired,
            SyncCompanionOutcome.Swept,
            SyncCompanionOutcome.AnchorPresent,
            SyncCompanionOutcome.Incomplete,
            null -> {
                BundleSaveResult.UnknownOutcome
            }
        }
    }

    private fun mapFetchChanges(message: SyncCompanionMessage): ChangeFetchResult {
        return when (message.outcome) {
            SyncCompanionOutcome.TokenExpired -> {
                ChangeFetchResult.TokenExpired
            }

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
            SyncCompanionOutcome.Swept,
            SyncCompanionOutcome.AnchorPresent,
            SyncCompanionOutcome.Incomplete,
            null -> {
                ChangeFetchResult.UnknownOutcome
            }
        }
    }

    private fun mapRecordDelete(message: SyncCompanionMessage): RecordDeleteResult {
        return when (message.outcome) {
            SyncCompanionOutcome.DeletedAndAbsent -> {
                RecordDeleteResult.DeletedAndAbsent
            }

            SyncCompanionOutcome.Retryable -> {
                RecordDeleteResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                RecordDeleteResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                RecordDeleteResult.UnknownOutcome
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
            SyncCompanionOutcome.TokenExpired,
            SyncCompanionOutcome.Swept,
            SyncCompanionOutcome.AnchorPresent,
            SyncCompanionOutcome.Incomplete,
            null -> {
                RecordDeleteResult.UnknownOutcome
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
        const val MAX_DELETE_ATTEMPTS: Int = 10
        const val ABSENT_FLAG: Byte = 0
        const val PRESENT_FLAG: Byte = 1
        const val LENGTH_BYTES: Int = 4
        const val MINIMUM_PAGE_BYTES: Int = 6
        const val MINIMUM_TAIL_BYTES: Int = 1
    }
}

private fun parseResumeCursor(payload: ByteArray): ByteArray? {
    if (payload.size !in 0..MacOsSyncCompanionProtocol.CURSOR_BYTES) {
        return null
    }
    return payload.copyOf()
}
