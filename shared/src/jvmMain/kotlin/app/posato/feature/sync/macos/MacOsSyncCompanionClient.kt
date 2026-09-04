package app.posato.feature.sync.macos

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface SyncCompanionTransport {
    suspend fun transact(message: SyncCompanionMessage): CompanionExchange

    fun newRequestIdentifier(): ByteArray
}

internal class MacOsSyncCompanionClient(
    private val executable: Path,
    private val arguments: List<String> = emptyList(),
    private val random: SecureRandom = SecureRandom(),
    private val onProcessStarted: (Process) -> Unit = {},
) : SyncCompanionTransport {
    override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
        return withContext(Dispatchers.IO) {
            val process = startProcess()
            onProcessStarted(process)
            try {
                val encoded = withTimeout(message.deadlineMilliseconds.toLong()) {
                    readFrameCancellable(process, message)
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
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                CompanionExchange.Unknown
            } catch (_: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
                CompanionExchange.Unknown
            } catch (_: IllegalStateException) {
                currentCoroutineContext().ensureActive()
                CompanionExchange.Unknown
            } finally {
                message.clear()
                terminate(process)
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

    private suspend fun readFrameCancellable(
        process: Process,
        message: SyncCompanionMessage,
    ): ByteArray {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                terminate(process)
            }
            val worker = Thread(
                {
                    try {
                        write(process, message)
                        process.outputStream.close()
                        continuation.resume(readFrame(process))
                    } catch (error: CancellationException) {
                        continuation.resumeWithException(error)
                    } catch (error: IOException) {
                        continuation.resumeWithException(error)
                    } catch (error: IllegalArgumentException) {
                        continuation.resumeWithException(error)
                    } catch (error: IllegalStateException) {
                        continuation.resumeWithException(error)
                    }
                },
                COMPANION_WORKER,
            )
            worker.isDaemon = true
            worker.start()
        }
    }

    private fun terminate(process: Process) {
        closeQuietly(process.outputStream)
        closeQuietly(process.inputStream)
        closeQuietly(process.errorStream)
        process.destroyForcibly()
    }

    private fun closeQuietly(stream: Closeable) {
        try {
            stream.close()
        } catch (_: IOException) {
            return
        }
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
        private const val COMPANION_WORKER: String = "posato-macos-sync-companion"

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
