package app.posato.desktop.mappings

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.posato.desktop.macos.MacOsApplicationPicker
import app.posato.desktop.macos.MacOsApplicationPickerResult
import app.posato.desktop.macos.MacOsHelperClient
import app.posato.desktop.macos.SelectedMacOsApplication
import app.posato.desktop.mappings.database.MacOsApplicationMappingsDatabase
import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingLimits
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.data.LocalApplicationRemovalFailure
import app.posato.feature.targets.data.LocalApplicationRemovalResult
import app.posato.feature.targets.data.LocalApplicationSelectionFailure
import app.posato.feature.targets.data.LocalApplicationSelectionRejection
import app.posato.feature.targets.data.LocalApplicationSelectionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.security.MessageDigest

internal class DesktopLocalApplicationMappings(
    private val files: ApplicationMappingFiles = MacOsApplicationMappingFiles(),
    private val picker: MacOsApplicationPicker = MacOsHelperClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalApplicationMappings,
    Closeable {
    private val operationMutex = Mutex()
    private var driver: JdbcSqliteDriver? = null
    private var database: MacOsApplicationMappingsDatabase? = null

    override suspend fun load(): LocalApplicationMappingsLoadResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    LocalApplicationMappingsLoadResult.Success(readSnapshot(openDatabase()))
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (_: CorruptApplicationMappingsException) {
                    LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.CORRUPTION)
                } catch (_: Exception) {
                    LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE)
                }
            }
        }
    }

    override suspend fun chooseApplications(): LocalApplicationSelectionResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                when (val result = picker.selectApplications()) {
                    is MacOsApplicationPickerResult.Success -> persistSelection(result.applications)
                    MacOsApplicationPickerResult.Cancelled -> LocalApplicationSelectionResult.Cancelled
                    MacOsApplicationPickerResult.SelfSelection -> rejected(LocalApplicationSelectionRejection.SELF)
                    MacOsApplicationPickerResult.InvalidOrUnsigned -> rejected(LocalApplicationSelectionRejection.INVALID_OR_UNSIGNED)
                    MacOsApplicationPickerResult.CapacityExceeded -> rejected(LocalApplicationSelectionRejection.CAPACITY)
                    MacOsApplicationPickerResult.Failure -> failure(LocalApplicationSelectionFailure.PICKER)
                }
            }
        }
    }

    override suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    val currentDatabase = openDatabase()
                    val snapshot = currentDatabase.transactionWithResult {
                        currentDatabase.applicationMappingsQueries.removeById(mappingId.canonicalValue.hexToByteArray())
                        readSnapshot(currentDatabase)
                    }
                    LocalApplicationRemovalResult.Success(snapshot)
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (_: Exception) {
                    LocalApplicationRemovalResult.Failure(LocalApplicationRemovalFailure.STORAGE)
                }
            }
        }
    }

    override fun close() {
        driver?.close()
        picker.closeIfOwned()
        driver = null
        database = null
    }

    private fun persistSelection(applications: List<SelectedMacOsApplication>): LocalApplicationSelectionResult {
        return try {
            val candidates = applications.map(::restoreCandidate).distinctBy { candidate -> candidate.mapping.id }
            val currentDatabase = openDatabase()
            val snapshot = currentDatabase.transactionWithResult {
                candidates.forEach { candidate ->
                    currentDatabase.applicationMappingsQueries.insertOrIgnore(
                        mappingId = candidate.mapping.id.canonicalValue.hexToByteArray(),
                        displayName = candidate.mapping.displayName.encodeToByteArray(),
                        designatedRequirement = candidate.designatedRequirement,
                    )
                }
                if (currentDatabase.applicationMappingsQueries.countAll().executeAsOne() > LocalApplicationMappingLimits.MAXIMUM_MAPPINGS) {
                    throw ApplicationMappingCapacityException()
                }
                readSnapshot(currentDatabase)
            }
            LocalApplicationSelectionResult.Success(snapshot)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: ApplicationMappingCapacityException) {
            rejected(LocalApplicationSelectionRejection.CAPACITY)
        } catch (_: CorruptApplicationMappingsException) {
            failure(LocalApplicationSelectionFailure.STORAGE)
        } catch (_: Exception) {
            failure(LocalApplicationSelectionFailure.STORAGE)
        }
    }

    @Suppress("ThrowsCount")
    private fun restoreCandidate(application: SelectedMacOsApplication): StoredApplicationCandidate {
        if (application.designatedRequirement.isEmpty() || application.designatedRequirement.size > MAXIMUM_REQUIREMENT_BYTES) {
            throw CorruptApplicationMappingsException()
        }
        val mappingId = LocalApplicationMappingId.restore(application.designatedRequirement.sha256().toHex())
            ?: throw CorruptApplicationMappingsException()
        val mapping = LocalApplicationMapping.restore(mappingId, application.displayName)
            ?: throw CorruptApplicationMappingsException()

        return StoredApplicationCandidate(mapping, application.designatedRequirement.copyOf())
    }

    private fun openDatabase(): MacOsApplicationMappingsDatabase {
        database?.let { current ->
            files.secureDatabaseArtifacts()
            return current
        }
        files.prepare()
        val openedDriver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${files.path}",
            schema = MacOsApplicationMappingsDatabase.Schema,
        )
        val openedDatabase = MacOsApplicationMappingsDatabase(openedDriver)
        files.secureDatabaseArtifacts()
        driver = openedDriver
        database = openedDatabase

        return openedDatabase
    }

    private fun readSnapshot(currentDatabase: MacOsApplicationMappingsDatabase): LocalApplicationMappingsSnapshot {
        val mappings = currentDatabase.applicationMappingsQueries.selectAll { mappingId, displayName, designatedRequirement ->
            restoreStoredMapping(mappingId, displayName, designatedRequirement)
        }.executeAsList()
        files.secureDatabaseArtifacts()

        return LocalApplicationMappingsSnapshot.restore(mappings) ?: throw CorruptApplicationMappingsException()
    }

    @Suppress("ThrowsCount")
    private fun restoreStoredMapping(
        mappingId: ByteArray,
        displayName: ByteArray,
        designatedRequirement: ByteArray,
    ): LocalApplicationMapping {
        if (mappingId.size != SHA256_BYTES || designatedRequirement.isEmpty() || designatedRequirement.size > MAXIMUM_REQUIREMENT_BYTES) {
            throw CorruptApplicationMappingsException()
        }
        if (!mappingId.contentEquals(designatedRequirement.sha256())) {
            throw CorruptApplicationMappingsException()
        }
        val restoredId = LocalApplicationMappingId.restore(mappingId.toHex()) ?: throw CorruptApplicationMappingsException()
        val restoredName = try {
            displayName.decodeToString(throwOnInvalidSequence = true)
        } catch (_: Exception) {
            throw CorruptApplicationMappingsException()
        }

        return LocalApplicationMapping.restore(restoredId, restoredName) ?: throw CorruptApplicationMappingsException()
    }

    private companion object {
        const val SHA256_BYTES: Int = 32
        const val MAXIMUM_REQUIREMENT_BYTES: Int = 4_096
    }
}

private class StoredApplicationCandidate(
    val mapping: LocalApplicationMapping,
    val designatedRequirement: ByteArray,
)

private class ApplicationMappingCapacityException : Exception()

private class CorruptApplicationMappingsException : Exception()

private fun ByteArray.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this)
}

private fun ByteArray.toHex(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun String.hexToByteArray(): ByteArray {
    return chunked(HEX_PAIR_LENGTH).map { pair -> pair.toInt(HEX_RADIX).toByte() }.toByteArray()
}

private const val HEX_PAIR_LENGTH: Int = 2
private const val HEX_RADIX: Int = 16

private fun rejected(reason: LocalApplicationSelectionRejection): LocalApplicationSelectionResult {
    return LocalApplicationSelectionResult.Rejected(reason)
}

private fun failure(reason: LocalApplicationSelectionFailure): LocalApplicationSelectionResult {
    return LocalApplicationSelectionResult.Failure(reason)
}

private fun MacOsApplicationPicker.closeIfOwned() {
    if (this is Closeable) {
        close()
    }
}
