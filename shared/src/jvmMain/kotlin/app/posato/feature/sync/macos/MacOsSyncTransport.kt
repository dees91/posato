package app.posato.feature.sync.macos

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.name

internal fun defaultSyncCompanionTransport(onProcessStarted: (Process) -> Unit = {}): SyncCompanionTransport {
    return object : SyncCompanionTransport {
        private val delegate by lazy { resolveTransport(onProcessStarted) }

        override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
            return delegate.transact(message)
        }

        override fun newRequestIdentifier(): ByteArray {
            return delegate.newRequestIdentifier()
        }
    }
}

internal fun applicationBundleRoot(): Path {
    val command = ProcessHandle.current().info().command().orElse(null)
    val executable = checkNotNull(command?.let { Path.of(it) }) { "Packaged application was not found" }
    val resolved = executable.toRealPath()

    return generateSequence(resolved.parent) { path -> path.parent }
        .take(MAXIMUM_BUNDLE_PARENT_DEPTH)
        .firstOrNull { candidate -> candidate.name.endsWith(".app") }
        ?: error("Packaged application was not found")
}

internal class UnavailableSyncCompanionTransport : SyncCompanionTransport {
    override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
        return try {
            CompanionExchange.Unknown
        } finally {
            message.clear()
        }
    }

    override fun newRequestIdentifier(): ByteArray {
        return ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES)
    }
}

private fun resolveTransport(onProcessStarted: (Process) -> Unit): SyncCompanionTransport {
    return try {
        MacOsSyncCompanionClient.verified(applicationBundleRoot(), onProcessStarted = onProcessStarted)
    } catch (_: IllegalStateException) {
        UnavailableSyncCompanionTransport()
    } catch (_: IOException) {
        UnavailableSyncCompanionTransport()
    }
}

private const val MAXIMUM_BUNDLE_PARENT_DEPTH: Int = 16
