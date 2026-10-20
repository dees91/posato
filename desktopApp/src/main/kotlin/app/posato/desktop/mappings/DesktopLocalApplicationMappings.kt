package app.posato.desktop.mappings

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.posato.desktop.macos.MacOsApplicationPicker
import app.posato.desktop.macos.MacOsApplicationPickerResult
import app.posato.desktop.macos.MacOsHelperClient
import app.posato.desktop.macos.SelectedMacOsApplication
import app.posato.desktop.mappings.database.MacOsApplicationMappingsDatabase
import app.posato.feature.targets.data.ApplicationChoiceSet
import app.posato.feature.targets.data.KeptApplication
import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingDisplay
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

    override suspend fun load(set: ApplicationChoiceSet): LocalApplicationMappingsLoadResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    LocalApplicationMappingsLoadResult.Success(openDatabase().snapshotOf(set, files))
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

    override suspend fun chooseApplications(set: ApplicationChoiceSet): LocalApplicationSelectionResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                when (val result = picker.selectApplications()) {
                    is MacOsApplicationPickerResult.Success -> persistSelection(result.applications, set)
                    MacOsApplicationPickerResult.Cancelled -> LocalApplicationSelectionResult.Cancelled
                    MacOsApplicationPickerResult.SelfSelection -> rejected(LocalApplicationSelectionRejection.SELF)
                    MacOsApplicationPickerResult.InvalidOrUnsigned -> rejected(LocalApplicationSelectionRejection.INVALID_OR_UNSIGNED)
                    MacOsApplicationPickerResult.CapacityExceeded -> rejected(LocalApplicationSelectionRejection.CAPACITY)
                    MacOsApplicationPickerResult.SystemApplication -> rejected(LocalApplicationSelectionRejection.SYSTEM)
                    MacOsApplicationPickerResult.Failure -> failure(LocalApplicationSelectionFailure.PICKER)
                }
            }
        }
    }

    override suspend fun remove(
        mappingId: LocalApplicationMappingId,
        set: ApplicationChoiceSet,
    ): LocalApplicationRemovalResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    val currentDatabase = openDatabase()
                    val snapshot = currentDatabase.transactionWithResult {
                        currentDatabase.applicationMappingsQueries.removeById(set.hex.hexToByteArray(), mappingId.canonicalValue.hexToByteArray())
                        currentDatabase.snapshotOf(set, files)
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

    override suspend fun clear(set: ApplicationChoiceSet): LocalApplicationRemovalResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    val currentDatabase = openDatabase()
                    currentDatabase.applicationMappingsQueries.removeSet(set.hex.hexToByteArray())
                    LocalApplicationRemovalResult.Success(LocalApplicationMappingsSnapshot.empty())
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (_: Exception) {
                    LocalApplicationRemovalResult.Failure(LocalApplicationRemovalFailure.STORAGE)
                }
            }
        }
    }

    override suspend fun copyChoices(
        from: ApplicationChoiceSet,
        to: ApplicationChoiceSet,
    ): LocalApplicationRemovalResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    val currentDatabase = openDatabase()
                    val snapshot = currentDatabase.transactionWithResult {
                        currentDatabase.applicationMappingsQueries.removeSet(to.hex.hexToByteArray())
                        currentDatabase.applicationMappingsQueries.copySet(to.hex.hexToByteArray(), from.hex.hexToByteArray())
                        currentDatabase.snapshotOf(to, files)
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

    override suspend fun retainOnly(sets: Set<ApplicationChoiceSet>): LocalApplicationRemovalResult {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                try {
                    val currentDatabase = openDatabase()
                    currentDatabase.transaction {
                        currentDatabase.applicationMappingsQueries.selectSetIds().executeAsList()
                            .filter { setId -> sets.none { kept -> kept.hex == setId.toHex() } }
                            .forEach { setId -> currentDatabase.applicationMappingsQueries.removeSet(setId) }
                    }
                    files.secureDatabaseArtifacts()
                    LocalApplicationRemovalResult.Success(LocalApplicationMappingsSnapshot.empty())
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

    override suspend fun keptApplications(mappingIds: Set<String>): List<KeptApplication> {
        if (mappingIds.isEmpty()) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                val kept = openDatabase().applicationMappingsQueries.selectKept().executeAsList()
                    .filter { row -> row.mappingId.toHex() in mappingIds }
                    .distinctBy { row -> row.mappingId.toHex() }
                    .map { row -> KeptApplication(row.mappingId.copyOf(), row.displayName.copyOf(), row.designatedRequirement.copyOf()) }
                files.secureDatabaseArtifacts()
                kept
            }
        }
    }

    /** Every chosen app's designated requirement by its canonical identifier, across all sets. */
    internal suspend fun storedRequirements(): Map<String, ByteArray> {
        return withContext(ioDispatcher) {
            operationMutex.withLock {
                val currentDatabase = openDatabase()
                val stored = currentDatabase.applicationMappingsQueries.selectRequirements { mappingId, designatedRequirement ->
                    mappingId.toHex() to designatedRequirement.copyOf()
                }.executeAsList().toMap()
                files.secureDatabaseArtifacts()
                stored
            }
        }
    }

    private fun persistSelection(
        applications: List<SelectedMacOsApplication>,
        set: ApplicationChoiceSet,
    ): LocalApplicationSelectionResult {
        return try {
            val candidates = applications.map(::restoreCandidate).distinctBy { candidate -> candidate.mapping.id }
            val currentDatabase = openDatabase()
            val snapshot = currentDatabase.transactionWithResult {
                candidates.forEach { candidate ->
                    val display = candidate.mapping.display as? LocalApplicationMappingDisplay.Named
                        ?: corruptApplicationMappings()
                    currentDatabase.applicationMappingsQueries.insertOrIgnore(
                        setId = set.hex.hexToByteArray(),
                        mappingId = candidate.mapping.id.canonicalValue.hexToByteArray(),
                        displayName = display.value.encodeToByteArray(),
                        designatedRequirement = candidate.designatedRequirement,
                    )
                }
                if (currentDatabase.applicationMappingsQueries.countUnique().executeAsOne() > LocalApplicationMappingLimits.MAXIMUM_MAPPINGS) {
                    throw ApplicationMappingCapacityException()
                }
                currentDatabase.snapshotOf(set, files)
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
}

/**
 * The first set's choices with their requirements, which parts running at the pause set upgrade keep. A store
 * that cannot be read now throws, so the upgrade stays pending; a corrupt one has no choices it could enforce.
 */
internal suspend fun DesktopLocalApplicationMappings.keptApplications(): List<KeptApplication> {
    val mappings = when (val loaded = load(ApplicationChoiceSet.FIRST)) {
        is LocalApplicationMappingsLoadResult.Success -> {
            loaded.snapshot.mappings
        }

        is LocalApplicationMappingsLoadResult.Unavailable -> {
            loaded.snapshot.mappings
        }

        is LocalApplicationMappingsLoadResult.Failure -> {
            check(loaded.reason == LocalApplicationMappingsLoadFailure.CORRUPTION) { "application choices unreadable" }
            emptyList()
        }
    }
    val requirements = designatedRequirements(mappings.map(LocalApplicationMapping::id))
    return mappings.zip(requirements).map { (mapping, requirement) ->
        val name = (mapping.display as? LocalApplicationMappingDisplay.Named)?.value.orEmpty()
        KeptApplication(mapping.id.canonicalValue.hexToByteArray(), name.encodeToByteArray(), requirement)
    }
}

internal suspend fun DesktopLocalApplicationMappings.designatedRequirements(ids: List<LocalApplicationMappingId>): List<ByteArray> {
    val stored = storedRequirements()
    return ids.map { id -> stored[id.canonicalValue] ?: throw IllegalArgumentException("Unknown application mapping") }
}

private fun restoreCandidate(application: SelectedMacOsApplication): StoredApplicationCandidate {
    if (application.designatedRequirement.isEmpty() || application.designatedRequirement.size > MAXIMUM_REQUIREMENT_BYTES) {
        corruptApplicationMappings()
    }
    val mappingId = LocalApplicationMappingId.restore(application.designatedRequirement.sha256().toHex())
        ?: corruptApplicationMappings()
    val mapping = LocalApplicationMapping.restore(mappingId, application.displayName)
        ?: corruptApplicationMappings()

    return StoredApplicationCandidate(mapping, application.designatedRequirement.copyOf())
}

private class StoredApplicationCandidate(
    val mapping: LocalApplicationMapping,
    val designatedRequirement: ByteArray,
)

private class ApplicationMappingCapacityException : Exception()

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
