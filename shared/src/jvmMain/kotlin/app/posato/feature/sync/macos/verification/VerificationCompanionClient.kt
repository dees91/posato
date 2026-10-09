package app.posato.feature.sync.macos.verification

import app.posato.feature.sync.macos.MacOsSyncCompanionProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** The verification-only companion operations of the ADR 0007 amendment of 2026-10-09, outside the product set. */
internal enum class VerificationSeamOperation(
    val code: Byte,
) {
    DeleteZone(201.toByte()),
}

internal enum class VerificationSeamOutcome {
    DeletedAndAbsent,
    AlreadyAbsent,
    AnchorPresent,
    Refused,
    Retryable,
    AccountChanged,
    Unknown,
}

/**
 * Talks to the companion for verification-only operations with its own framing and decoder, so the product
 * client and its operation set never see these codes. Every response must carry the seam marker; a release
 * companion rejects the code, which reads here as [VerificationSeamOutcome.Unknown].
 */
internal class VerificationCompanionClient(
    private val executable: Path,
    private val random: SecureRandom = SecureRandom(),
) {
    suspend fun run(
        operation: VerificationSeamOperation,
        binding: ByteArray,
    ): VerificationSeamOutcome {
        require(binding.size == MacOsSyncCompanionProtocol.BINDING_BYTES)
        val identifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES).also(random::nextBytes)
        identifier[0] = (identifier[0].toInt() or 1).toByte()
        val response = withContext(Dispatchers.IO) { exchange(frame(operation, identifier, binding)) }
            ?: return VerificationSeamOutcome.Unknown
        return decode(response, operation, identifier)
    }

    private fun frame(
        operation: VerificationSeamOperation,
        identifier: ByteArray,
        payload: ByteArray,
    ): ByteArray {
        return ByteBuffer.allocate(MacOsSyncCompanionProtocol.HEADER_BYTES + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(MacOsSyncCompanionProtocol.MAGIC)
            .putShort(MacOsSyncCompanionProtocol.MAJOR_VERSION)
            .put(operation.code)
            .put(identifier)
            .putInt(DEADLINE_MILLISECONDS)
            .putLong(MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY)
            .put(0)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    private fun exchange(frame: ByteArray): ByteArray? {
        val builder = ProcessBuilder(executable.toString())
        builder.environment().clear()
        System.getenv("HOME")?.let { home -> builder.environment()["HOME"] = home }
        val process = builder.start()
        return try {
            process.outputStream.use { stream ->
                stream.write(ByteBuffer.allocate(LENGTH_PREFIX_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(frame.size).array())
                stream.write(frame)
            }
            val finished = process.waitFor(DEADLINE_MILLISECONDS + GRACE_MILLISECONDS, TimeUnit.MILLISECONDS)
            if (finished) unframe(process.inputStream.readAllBytes()) else null
        } catch (_: IOException) {
            null
        } finally {
            process.destroyForcibly()
        }
    }

    private fun unframe(output: ByteArray): ByteArray? {
        if (output.size < LENGTH_PREFIX_BYTES) return null
        val size = ByteBuffer.wrap(output, 0, LENGTH_PREFIX_BYTES).order(ByteOrder.BIG_ENDIAN).int
        val complete = size == output.size - LENGTH_PREFIX_BYTES && size <= MacOsSyncCompanionProtocol.MAXIMUM_FRAME_BYTES
        return if (complete) output.copyOfRange(LENGTH_PREFIX_BYTES, output.size) else null
    }

    private fun decode(
        response: ByteArray,
        operation: VerificationSeamOperation,
        identifier: ByteArray,
    ): VerificationSeamOutcome {
        if (response.size < MacOsSyncCompanionProtocol.HEADER_BYTES) return VerificationSeamOutcome.Unknown
        val buffer = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN)
        val matches = buffer.int == MacOsSyncCompanionProtocol.MAGIC &&
            buffer.short == MacOsSyncCompanionProtocol.MAJOR_VERSION &&
            buffer.get() == operation.code &&
            ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES).also(buffer::get).contentEquals(identifier)
        if (!matches) return VerificationSeamOutcome.Unknown
        buffer.int
        buffer.long
        val outcome = buffer.get()
        val payload = ByteArray(buffer.int.coerceIn(0, buffer.remaining())).also(buffer::get)
        if (!payload.contentEquals(MARKER)) return VerificationSeamOutcome.Unknown
        return when (outcome) {
            MacOsSyncCompanionProtocol.OUTCOME_DELETED -> VerificationSeamOutcome.DeletedAndAbsent
            MacOsSyncCompanionProtocol.OUTCOME_MISSING -> VerificationSeamOutcome.AlreadyAbsent
            MacOsSyncCompanionProtocol.OUTCOME_ANCHOR_PRESENT -> VerificationSeamOutcome.AnchorPresent
            MacOsSyncCompanionProtocol.OUTCOME_RESTRICTED -> VerificationSeamOutcome.Refused
            MacOsSyncCompanionProtocol.OUTCOME_RETRYABLE -> VerificationSeamOutcome.Retryable
            MacOsSyncCompanionProtocol.OUTCOME_ACCOUNT_CHANGED -> VerificationSeamOutcome.AccountChanged
            else -> VerificationSeamOutcome.Unknown
        }
    }

    companion object {
        /** The seam marker; release companions never carry it (ADR 0007 amendment of 2026-10-09). */
        val MARKER: ByteArray = "posato-verification-seams-v1".toByteArray()
        private const val DEADLINE_MILLISECONDS: Int = 60_000
        private const val GRACE_MILLISECONDS: Long = 5_000
        private const val LENGTH_PREFIX_BYTES: Int = 4
    }
}
