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
    /**
     * Continuation state across the retry boundary, one slot per operation.
     * A removal attempt that ends in [RecordDeleteResult.Retryable] keeps its
     * cursor here, so the next attempt resumes past the banked pages instead
     * of restarting from nil. Entries are keyed by binding bytes: a mismatch
     * drops the stored token, and terminal outcomes clear their slot. Tokens
     * are opaque server cursors, so resuming one is always safe — deletes
     * stay idempotent and every resumed pass re-validates before deleting.
     */
    private var deleteContinuation: MailboxDeleteContinuation? = null
    private var sweepContinuation: MailboxDeleteContinuation? = null

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
        val resumed = resumeFrom(deleteContinuation, binding)
        deleteContinuation = resumed.first
        var cursor: ByteArray? = resumed.second
        return try {
            repeat(MAX_DELETE_ATTEMPTS) {
                val payload = if (cursor == null) {
                    MacOsSyncCompanionProtocol.cloudPayload(binding)
                } else {
                    MacOsSyncCompanionProtocol.deleteResumePayload(binding, checkNotNull(cursor))
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
                            val next = parseResumeCursor(
                                response.message.payload,
                                MacOsSyncCompanionProtocol.DELETE_RESUME_TOKEN_BYTES,
                            )
                            if (next == null) {
                                // Keep the last good cursor: a malformed
                                // checkpoint carries no progress past it.
                                return RecordDeleteResult.UnknownOutcome
                            }
                            cursor?.fill(0)
                            cursor = next
                            deleteContinuation = storeContinuation(deleteContinuation, binding, next)
                        } else {
                            if (isDeleteTerminal(response.message.outcome)) {
                                clearContinuation(deleteContinuation)
                                deleteContinuation = null
                            }
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
        val resumed = resumeFrom(sweepContinuation, binding)
        sweepContinuation = resumed.first
        var cursor: ByteArray? = resumed.second
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
                            val next = parseResumeCursor(
                                response.message.payload,
                                MacOsSyncCompanionProtocol.CURSOR_BYTES,
                            )
                            if (next == null) {
                                // Keep the last good cursor: a malformed
                                // checkpoint carries no progress past it.
                                return BundleSweepResult.UnknownOutcome
                            }
                            cursor?.fill(0)
                            cursor = next
                            sweepContinuation = storeContinuation(sweepContinuation, binding, next)
                        } else {
                            if (isSweepTerminal(response.message.outcome)) {
                                clearContinuation(sweepContinuation)
                                sweepContinuation = null
                            }
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

private fun parseResumeCursor(
    payload: ByteArray,
    limit: Int,
): ByteArray? {
    if (payload.size !in 0..limit) {
        return null
    }
    return payload.copyOf()
}

internal class MailboxDeleteContinuation(
    val binding: ByteArray,
    val token: ByteArray,
) {
    fun clear() {
        binding.fill(0)
        token.fill(0)
    }
}

private fun resumeFrom(
    stored: MailboxDeleteContinuation?,
    binding: ByteArray,
): Pair<MailboxDeleteContinuation?, ByteArray?> {
    if (stored != null && stored.binding.contentEquals(binding)) {
        return stored to stored.token.copyOf()
    }
    stored?.clear()
    return null to null
}

private fun storeContinuation(
    previous: MailboxDeleteContinuation?,
    binding: ByteArray,
    token: ByteArray,
): MailboxDeleteContinuation {
    previous?.clear()
    return MailboxDeleteContinuation(binding.copyOf(), token.copyOf())
}

private fun clearContinuation(stored: MailboxDeleteContinuation?) {
    stored?.clear()
}

private fun isDeleteTerminal(outcome: SyncCompanionOutcome?): Boolean {
    return outcome == SyncCompanionOutcome.DeletedAndAbsent ||
        outcome == SyncCompanionOutcome.AccountChanged
}

private fun isSweepTerminal(outcome: SyncCompanionOutcome?): Boolean {
    return outcome == SyncCompanionOutcome.Swept ||
        outcome == SyncCompanionOutcome.AnchorPresent ||
        outcome == SyncCompanionOutcome.AccountChanged
}
