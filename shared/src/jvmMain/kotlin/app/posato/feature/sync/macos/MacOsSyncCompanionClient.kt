package app.posato.feature.sync.macos

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom

internal interface SyncCompanionTransport {
    suspend fun transact(message: SyncCompanionMessage): CompanionExchange

    fun newRequestIdentifier(): ByteArray
}

internal class MacOsSyncCompanionClient(
    private val executable: Path,
    private val arguments: List<String> = emptyList(),
    private val random: SecureRandom = SecureRandom(),
) : SyncCompanionTransport {
    override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
        return withContext(Dispatchers.IO) {
            val process = startProcess()
            try {
                val encoded = withTimeout(message.deadlineMilliseconds.toLong()) {
                    runInterruptible {
                        write(process, message)
                        process.outputStream.close()
                        readFrame(process)
                    }
                }
                val response = MacOsSyncCompanionProtocol.decode(encoded)
                if (matchesRequest(message, response)) {
                    CompanionExchange.Message(response)
                } else {
                    CompanionExchange.Unknown
                }
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException) {
                    CompanionExchange.Unknown
                } else {
                    throw error
                }
            } catch (_: java.io.IOException) {
                CompanionExchange.Unknown
            } catch (_: IllegalArgumentException) {
                CompanionExchange.Unknown
            } catch (_: IllegalStateException) {
                CompanionExchange.Unknown
            } finally {
                message.clear()
                process.destroyForcibly()
            }
        }
    }

    override fun newRequestIdentifier(): ByteArray {
        return ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES).also { bytes ->
            random.nextBytes(bytes)
        }
    }

    private fun startProcess(): Process {
        check(Files.isRegularFile(executable))
        check(Files.isExecutable(executable))
        val command = buildList {
            add(executable.toString())
            addAll(arguments)
        }
        val builder = ProcessBuilder(command)
        builder.environment().clear()
        System.getenv("HOME")?.let { home ->
            builder.environment()["HOME"] = home
        }
        return builder.start()
    }

    private fun write(
        process: Process,
        message: SyncCompanionMessage,
    ) {
        val encoded = MacOsSyncCompanionProtocol.encode(message)
        val stream = BufferedOutputStream(process.outputStream)
        val prefix = ByteBuffer.allocate(LENGTH_PREFIX_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(encoded.size)
            .array()
        stream.write(prefix)
        stream.write(encoded)
        stream.flush()
        encoded.fill(0)
    }

    private fun readFrame(process: Process): ByteArray {
        val stream = BufferedInputStream(process.inputStream)
        val prefix = stream.readNBytes(LENGTH_PREFIX_BYTES)
        if (prefix.size != LENGTH_PREFIX_BYTES) {
            throw EOFException("Companion response ended")
        }
        val size = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN).int
        require(size in 1..MacOsSyncCompanionProtocol.MAXIMUM_FRAME_BYTES)
        val encoded = stream.readNBytes(size)
        if (encoded.size != size) {
            throw EOFException("Companion response was incomplete")
        }
        return encoded
    }

    private fun matchesRequest(
        request: SyncCompanionMessage,
        response: SyncCompanionMessage,
    ): Boolean {
        return response.operation == request.operation &&
            response.requestIdentifier.contentEquals(request.requestIdentifier) &&
            response.outcome != null
    }

    companion object {
        private const val LENGTH_PREFIX_BYTES: Int = 4

        fun verified(
            applicationRoot: Path,
            verifier: MacOsSyncCompanionVerifier = MacOsSyncCompanionVerifier(),
        ): MacOsSyncCompanionClient {
            return MacOsSyncCompanionClient(verifier.verify(applicationRoot))
        }
    }
}

internal sealed interface CompanionExchange {
    data class Message(
        val message: SyncCompanionMessage,
    ) : CompanionExchange {
        override fun toString(): String {
            return "CompanionExchange.Message(redacted)"
        }
    }

    data object Unknown : CompanionExchange
}
