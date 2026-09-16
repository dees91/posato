@file:Suppress("MagicNumber", "TooManyFunctions")

package app.posato.desktop.macos

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Parent-side client of the normal-user helper. [helperPath] defaults to the helper embedded in the
 * running packaged application. [launchPrefix] is a test-only parent process signed as the
 * application, which the maintainer-gated physical harness needs because the helper accepts only
 * a parent carrying the application identifier; production leaves it empty.
 */
internal class MacOsHelperClient(
    helperPath: Path? = null,
    private val launchPrefix: List<String> = emptyList(),
) : Closeable,
    MacHelperCommands,
    MacOsApplicationPicker,
    MacOsBrowserDomainCommands,
    MacOsApplicationCommands {
    private val helperPath: Path by lazy { helperPath ?: MacOsHelperSigningVerifier.installedHelperPath() }
    private val random = SecureRandom()
    private val readerExecutor = Executors.newSingleThreadExecutor()
    private val sessionIdentifier = randomIdentifier()

    @Volatile private var isClosed = false

    @Volatile private var activeSelectionProcess: Process? = null

    private var process: Process? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private var connectionIdentifier = ByteArray(MacOsHelperProtocol.IDENTIFIER_BYTES)
    private var nextSequence = 1
    private var pendingUnknownRequest: HelperMessage? = null

    @Synchronized
    override fun status(): HelperResult {
        return request(HelperOperation.Status)
    }

    @Synchronized
    override fun enable(): HelperResult {
        return request(HelperOperation.Enable)
    }

    @Synchronized
    fun repair(): HelperResult {
        return completeRepair(request(HelperOperation.Repair)) {
            reconcileUnknown()
        }
    }

    @Synchronized
    override fun configureBrowserDomains(
        domains: List<String>,
        sessionEndEpochMilliseconds: Long?,
    ): BrowserDomainConfigureResponse {
        val payload = BrowserDomainConfigurePayload(domains, sessionEndEpochMilliseconds).encode()
        return configureRequest(payload)
    }

    @Synchronized
    override fun configureApplications(
        requirements: List<ByteArray>,
        sessionEndEpochMilliseconds: Long?,
    ): ApplicationEnforcementResponse {
        val payload = ApplicationEnforcementPayload(requirements, sessionEndEpochMilliseconds).encode()
        return configureApplicationRequest(payload)
    }

    @Synchronized
    override fun apply(port: UShort): HelperResult {
        require(port > 0u)
        val payload = ByteBuffer.allocate(2)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(port.toShort())
            .array()
        return request(HelperOperation.Apply, payload)
    }

    @Synchronized
    override fun restore(): HelperResult {
        return request(HelperOperation.Restore)
    }

    @Synchronized
    fun disable(): HelperResult {
        return request(HelperOperation.Disable)
    }

    @Synchronized
    override fun remove(): HelperRemovalAttempt {
        return removeAfterReconciling(
            pendingOperation = pendingUnknownRequest?.operation,
            reconcileEarlier = ::reconcileUnknown,
            sendRemove = { request(HelperOperation.Remove) },
        )
    }

    @Synchronized
    override fun selectApplications(): MacOsApplicationPickerResult {
        if (isClosed || pendingUnknownRequest != null) {
            return MacOsApplicationPickerResult.Failure
        }
        return try {
            ensureStarted()
            activeSelectionProcess = checkNotNull(process)
            check(!isClosed)
            check(nextSequence <= MacOsHelperProtocol.MAXIMUM_OPERATIONS)
            val requestIdentifier = randomIdentifier()
            val message = HelperMessage(
                kind = HelperMessageKind.Request,
                operation = HelperOperation.SelectApplications,
                sequence = nextSequence++,
                deadlineMilliseconds = MacOsHelperProtocol.MAXIMUM_SELECTION_DEADLINE_MILLISECONDS,
                connectionIdentifier = connectionIdentifier,
                sessionIdentifier = sessionIdentifier,
                requestIdentifier = requestIdentifier,
                payload = byteArrayOf(),
            )
            write(message)
            val response = readWithDeadline(message.deadlineMilliseconds.toLong())
            check(response.kind == HelperMessageKind.Response)
            check(response.operation == message.operation)
            check(response.sequence == message.sequence)
            check(response.connectionIdentifier.contentEquals(connectionIdentifier))
            check(response.sessionIdentifier.contentEquals(sessionIdentifier))
            check(response.requestIdentifier.contentEquals(requestIdentifier))
            MacOsApplicationSelectionProtocol.decode(response.payload).also {
                terminateProcess()
            }
        } catch (_: Exception) {
            terminateProcess()
            MacOsApplicationPickerResult.Failure
        } finally {
            activeSelectionProcess = null
        }
    }

    @Synchronized
    override fun reconcileUnknown(): HelperResult {
        val pending = pendingUnknownRequest ?: return status()
        val result = request(
            operation = HelperOperation.Reconcile,
            payload = byteArrayOf(pending.operation.code) +
                MacOsHelperProtocol.canonicalInputDigest(pending.operation, pending.payload),
            requestIdentifier = pending.requestIdentifier,
        )
        if (result.concludesReconciliation()) {
            pendingUnknownRequest = null
        }
        return result
    }

    /**
     * Test-only crash simulation: kills the spawned helper without touching the launcher process.
     * A `pkill -f` pattern is unsafe here because the harness launcher command line itself embeds
     * the helper path, so this resolves the helper as the launcher's child instead of matching text.
     */
    fun destroySpawnedHelper() {
        val launcher = synchronized(this) { process } ?: return
        launcher.children().forEach { helper -> helper.destroyForcibly() }
    }

    override fun close() {
        isClosed = true
        activeSelectionProcess?.let { selectionProcess ->
            selectionProcess.destroy()
            if (!selectionProcess.waitFor(1, TimeUnit.SECONDS)) {
                selectionProcess.destroyForcibly()
            }
        }
        synchronized(this) {
            runCatching {
                if (process?.isAlive == true) {
                    request(HelperOperation.Restore)
                }
            }
            runCatching { output?.close() }
            runCatching { input?.close() }
            process?.destroy()
            readerExecutor.shutdownNow()
            output = null
            input = null
            process = null
        }
    }

    private fun configureApplicationRequest(payload: ByteArray): ApplicationEnforcementResponse {
        return helperOnlyRequest(
            operation = HelperOperation.ConfigureApplications,
            payload = payload,
            decode = ApplicationEnforcementResponse::decode,
            unknown = { ApplicationEnforcementResponse(HelperResult.unknownOutcome(), 0) },
        )
    }

    private fun configureRequest(payload: ByteArray): BrowserDomainConfigureResponse {
        return helperOnlyRequest(
            operation = HelperOperation.ConfigureBrowserDomains,
            payload = payload,
            decode = BrowserDomainConfigureResponse::decode,
            unknown = { BrowserDomainConfigureResponse(HelperResult.unknownOutcome(), 0.toUShort()) },
        )
    }

    private fun <T> helperOnlyRequest(
        operation: HelperOperation,
        payload: ByteArray,
        decode: (ByteArray) -> T,
        unknown: () -> T,
    ): T {
        check(pendingUnknownRequest == null)
        ensureStarted()
        check(nextSequence <= MacOsHelperProtocol.MAXIMUM_OPERATIONS)
        val requestIdentifier = randomIdentifier()
        val message = HelperMessage(
            kind = HelperMessageKind.Request,
            operation = operation,
            sequence = nextSequence++,
            deadlineMilliseconds = MacOsHelperProtocol.MAXIMUM_CONFIGURE_DEADLINE_MILLISECONDS,
            connectionIdentifier = connectionIdentifier,
            sessionIdentifier = sessionIdentifier,
            requestIdentifier = requestIdentifier,
            payload = payload,
        )
        return try {
            write(message)
            val response = readWithDeadline(message.deadlineMilliseconds.toLong())
            check(response.kind == HelperMessageKind.Response)
            check(response.operation == message.operation)
            check(response.sequence == message.sequence)
            check(response.connectionIdentifier.contentEquals(connectionIdentifier))
            check(response.sessionIdentifier.contentEquals(sessionIdentifier))
            check(response.requestIdentifier.contentEquals(requestIdentifier))
            decode(response.payload)
        } catch (_: Exception) {
            terminateProcess(cancellation = message)
            unknown()
        }
    }

    private fun request(
        operation: HelperOperation,
        payload: ByteArray = byteArrayOf(),
        requestIdentifier: ByteArray = randomIdentifier(),
    ): HelperResult {
        if (shouldReconcileUnknownRequest(pendingUnknownRequest != null, operation)) {
            return reconcileUnknown()
        }
        check(pendingUnknownRequest == null || operation == HelperOperation.Reconcile)
        ensureStarted()
        check(nextSequence <= MacOsHelperProtocol.MAXIMUM_OPERATIONS)
        val message = HelperMessage(
            kind = HelperMessageKind.Request,
            operation = operation,
            sequence = nextSequence++,
            deadlineMilliseconds = MacOsHelperProtocol.MAXIMUM_LIFECYCLE_DEADLINE_MILLISECONDS,
            connectionIdentifier = connectionIdentifier,
            sessionIdentifier = sessionIdentifier,
            requestIdentifier = requestIdentifier,
            payload = payload,
        )
        return try {
            write(message)
            val response = readWithDeadline(message.deadlineMilliseconds.toLong())
            check(response.kind == HelperMessageKind.Response)
            check(response.operation == operation)
            check(response.sequence == message.sequence)
            check(response.connectionIdentifier.contentEquals(connectionIdentifier))
            check(response.sessionIdentifier.contentEquals(sessionIdentifier))
            check(response.requestIdentifier.contentEquals(requestIdentifier))
            HelperResult.decode(response.payload)
        } catch (_: Exception) {
            pendingUnknownRequest = retainPendingUnknownRequest(pendingUnknownRequest, message)
            terminateProcess(cancellation = message)
            HelperResult.unknownOutcome()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun ensureStarted() {
        if (process?.isAlive == true) {
            return
        }
        check(!isClosed)
        check(Files.isRegularFile(helperPath) && Files.isExecutable(helperPath))
        val verifiedHelper = MacOsHelperSigningVerifier.verify(helperPath)
        val builder = ProcessBuilder(launchPrefix + verifiedHelper.toString())
        builder.environment().clear()
        val started = builder.start()
        process = started
        input = BufferedInputStream(started.inputStream)
        output = BufferedOutputStream(started.outputStream)
        try {
            connectionIdentifier = ByteArray(MacOsHelperProtocol.IDENTIFIER_BYTES)
            nextSequence = 1
            val requestIdentifier = randomIdentifier()
            write(
                HelperMessage(
                    kind = HelperMessageKind.Hello,
                    operation = HelperOperation.None,
                    sequence = nextSequence++,
                    deadlineMilliseconds = 5_000,
                    connectionIdentifier = connectionIdentifier,
                    sessionIdentifier = sessionIdentifier,
                    requestIdentifier = requestIdentifier,
                    payload = MacOsHelperProtocol.capabilityPayload(),
                ),
            )
            val welcome = readWithDeadline(5_000)
            check(welcome.kind == HelperMessageKind.Welcome)
            check(welcome.operation == HelperOperation.None)
            check(welcome.sequence == 1)
            check(welcome.sessionIdentifier.contentEquals(sessionIdentifier))
            check(welcome.requestIdentifier.contentEquals(requestIdentifier))
            check(MacOsHelperProtocol.supportsRequiredParentCapabilities(welcome.payload))
            connectionIdentifier = welcome.connectionIdentifier
            check(connectionIdentifier.any { it != 0.toByte() })
        } catch (error: Exception) {
            terminateProcess()
            throw error
        }
    }

    private fun write(message: HelperMessage) {
        val encoded = MacOsHelperProtocol.encode(message)
        val stream = checkNotNull(output)
        val prefix = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(encoded.size)
            .array()
        stream.write(prefix)
        stream.write(encoded)
        stream.flush()
    }

    private fun read(): HelperMessage {
        val stream = checkNotNull(input)
        val prefix = stream.readNBytes(4)
        if (prefix.size != 4) {
            throw EOFException("Helper response ended")
        }
        val size = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN).int
        require(size in 1..MacOsHelperProtocol.MAXIMUM_FRAME_BYTES)
        val encoded = stream.readNBytes(size)
        if (encoded.size != size) {
            throw EOFException("Helper response was incomplete")
        }
        return MacOsHelperProtocol.decode(encoded)
    }

    @Suppress("ThrowsCount")
    private fun readWithDeadline(timeoutMilliseconds: Long): HelperMessage {
        val future = readerExecutor.submit<HelperMessage> { read() }
        return try {
            future.get(timeoutMilliseconds, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            future.cancel(true)
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            throw IllegalStateException("Helper response was cancelled")
        } catch (error: ExecutionException) {
            throw IllegalStateException("Helper response failed", error)
        }
    }

    private fun terminateProcess(cancellation: HelperMessage? = null) {
        if (cancellation != null && nextSequence <= MacOsHelperProtocol.MAXIMUM_OPERATIONS) {
            runCatching {
                write(
                    HelperMessage(
                        kind = HelperMessageKind.Cancel,
                        operation = HelperOperation.None,
                        sequence = nextSequence++,
                        deadlineMilliseconds = 250,
                        connectionIdentifier = connectionIdentifier,
                        sessionIdentifier = sessionIdentifier,
                        requestIdentifier = cancellation.requestIdentifier,
                        payload = byteArrayOf(),
                    ),
                )
                process?.waitFor(250, TimeUnit.MILLISECONDS)
            }
        }
        runCatching { output?.close() }
        runCatching { input?.close() }
        if (process?.isAlive == true) {
            process?.destroyForcibly()
        }
        output = null
        input = null
        process = null
    }

    private fun randomIdentifier(): ByteArray {
        return ByteArray(MacOsHelperProtocol.IDENTIFIER_BYTES).also {
            random.nextBytes(it)
        }
    }
}

internal fun completeRepair(
    result: HelperResult,
    reconcile: () -> HelperResult,
): HelperResult = if (result.outcome == HelperResult.Outcome.UnknownOutcome) reconcile() else result

internal fun retainPendingUnknownRequest(
    pending: HelperMessage?,
    failed: HelperMessage,
): HelperMessage = pending ?: failed

internal fun shouldReconcileUnknownRequest(
    pendingUnknown: Boolean,
    operation: HelperOperation,
): Boolean {
    return pendingUnknown &&
        (operation == HelperOperation.Enable || operation == HelperOperation.Status || operation == HelperOperation.Remove)
}

internal fun reconcilesEarlierRequestBeforeRemoval(pendingOperation: HelperOperation): Boolean {
    return pendingOperation != HelperOperation.Remove
}

internal fun removeAfterReconciling(
    pendingOperation: HelperOperation?,
    reconcileEarlier: () -> HelperResult,
    sendRemove: () -> HelperResult,
): HelperRemovalAttempt {
    if (pendingOperation != null && reconcilesEarlierRequestBeforeRemoval(pendingOperation)) {
        val earlier = reconcileEarlier()
        if (!earlier.concludesReconciliation()) {
            return HelperRemovalAttempt(earlier, concernsRemove = false)
        }
    }
    return HelperRemovalAttempt(sendRemove(), concernsRemove = true)
}

internal data class HelperRemovalAttempt(
    val result: HelperResult,
    val concernsRemove: Boolean,
)

/**
 * Only a reply that still says nothing about the original request keeps it pending: a lost reply and
 * the registered-but-unlaunchable setup tuple. Every other answer, including a daemon recovery
 * response for an apply or restore, is conclusive and releases the request.
 */
internal fun HelperResult.concludesReconciliation(): Boolean {
    return when (outcome) {
        HelperResult.Outcome.UnknownOutcome -> false
        HelperResult.Outcome.ActionRequired -> !isUnlaunchableRegistration()
        HelperResult.Outcome.Success, HelperResult.Outcome.Conflict, HelperResult.Outcome.Failure -> true
    }
}

internal fun HelperResult.isUnlaunchableRegistration(): Boolean {
    return outcome == HelperResult.Outcome.ActionRequired &&
        serviceState == HelperResult.State.RecoveryRequired &&
        ownershipPhase == HelperResult.Phase.RecoveryRequired &&
        requiredAction == HelperResult.RequiredAction.ManualRecovery &&
        failure == HelperResult.Failure.Lifecycle
}

internal data class HelperResult(
    val outcome: Outcome,
    val serviceState: State,
    val ownershipPhase: Phase,
    val requiredAction: RequiredAction,
    val failure: Failure,
) {
    internal enum class Outcome { Success, Conflict, ActionRequired, UnknownOutcome, Failure }

    internal enum class State { NotRegistered, ApprovalRequired, Ready, UnavailableOrIncompatible, RecoveryRequired }

    internal enum class Phase { Idle, Prepared, Applied, RestorePending, RecoveryRequired }

    internal enum class RequiredAction {
        None,
        BackgroundApproval,
        AdministratorAuthentication,
        RuleRepair,
        ProxyRecovery,
        ManualRecovery,
        Incompatible,
    }

    internal enum class Failure { None, InvalidInput, Unavailable, Permission, Timeout, Integrity, Storage, Ipc, Lifecycle, Cancelled }

    companion object {
        fun unknownOutcome(): HelperResult {
            return HelperResult(
                outcome = Outcome.UnknownOutcome,
                serviceState = State.RecoveryRequired,
                ownershipPhase = Phase.RecoveryRequired,
                requiredAction = RequiredAction.ProxyRecovery,
                failure = Failure.Timeout,
            )
        }

        fun decode(payload: ByteArray): HelperResult {
            require(payload.size == 5)
            return HelperResult(
                outcome = Outcome.entries.value(payload[0]),
                serviceState = State.entries.value(payload[1]),
                ownershipPhase = Phase.entries.value(payload[2]),
                requiredAction = RequiredAction.entries.value(payload[3], zeroBased = true),
                failure = Failure.entries.value(payload[4], zeroBased = true),
            )
        }

        private fun <T> List<T>.value(
            code: Byte,
            zeroBased: Boolean = false,
        ): T {
            val index = code.toInt() - if (zeroBased) 0 else 1
            require(index in indices)
            return this[index]
        }
    }
}
