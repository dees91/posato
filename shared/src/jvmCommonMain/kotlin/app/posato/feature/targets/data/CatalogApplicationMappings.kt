package app.posato.feature.targets.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** One installed application: [key] is stable on this device (a desktop entry id, a package), [target] is what a pause matches. */
public class CatalogEntry(
    public val key: String,
    public val name: String,
    public val target: String,
)

/** The applications a host offers and how the person picks among them. */
public interface ApplicationCatalog {
    public suspend fun installed(): List<CatalogEntry>

    /** Shows the choice with [chosen] keys selected, and returns the picked entries, or null when cancelled. */
    public suspend fun choose(
        entries: List<CatalogEntry>,
        chosen: Set<String>,
    ): List<CatalogEntry>?
}

/**
 * App choices on Linux and Android, kept per pause set in one local file. A mapping's identifier is the SHA-256 of the
 * entry's key, so the same application keeps its identifier across launches.
 */
public class CatalogApplicationMappings(
    private val file: Path,
    private val catalog: ApplicationCatalog,
    private val ioDispatcher: CoroutineDispatcher,
) : LocalApplicationMappings {
    private val mutex = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val invalidations: Flow<Unit> = changes.asSharedFlow()

    override suspend fun load(set: ApplicationChoiceSet): LocalApplicationMappingsLoadResult {
        return locked {
            val records = read() ?: return@locked LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE)
            snapshot(records, set)?.let { LocalApplicationMappingsLoadResult.Success(it) }
                ?: LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.CORRUPTION)
        }
    }

    override suspend fun chooseApplications(set: ApplicationChoiceSet): LocalApplicationSelectionResult {
        return locked {
            val records = read() ?: return@locked LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.STORAGE)
            val current = records.filter { it.set == set.hex }
            val entries = catalog.installed()
            val picked = catalog.choose(entries, current.map { it.key }.toSet()) ?: return@locked LocalApplicationSelectionResult.Cancelled
            val chosen = picked.map { Record(set.hex, it.key, it.name.clean(), it.target.clean()) }
            val next = records.filter { it.set != set.hex } + chosen
            if (next.map { it.key }.distinct().size > LocalApplicationMappingLimits.MAXIMUM_MAPPINGS) {
                return@locked LocalApplicationSelectionResult.Rejected(LocalApplicationSelectionRejection.CAPACITY)
            }
            if (!write(next)) return@locked LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.STORAGE)
            changes.tryEmit(Unit)
            snapshot(next, set)?.let { LocalApplicationSelectionResult.Success(it) }
                ?: LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.STORAGE)
        }
    }

    override suspend fun remove(
        mappingId: LocalApplicationMappingId,
        set: ApplicationChoiceSet,
    ): LocalApplicationRemovalResult {
        return change(set) { records -> records.filterNot { it.set == set.hex && it.id == mappingId.canonicalValue } }
    }

    override suspend fun clear(set: ApplicationChoiceSet): LocalApplicationRemovalResult {
        return change(set) { records -> records.filterNot { it.set == set.hex } }
    }

    override suspend fun retainOnly(sets: Set<ApplicationChoiceSet>): LocalApplicationRemovalResult {
        val kept = sets.map { it.hex }.toSet()
        return change(ApplicationChoiceSet.FIRST) { records -> records.filter { it.set in kept } }
    }

    /** The targets of the given mapping identifiers, from whichever set chose them. */
    public suspend fun targets(mappingIds: Collection<String>): List<String> {
        return locked { read().orEmpty().filter { it.id in mappingIds }.map { it.target }.distinct() }
    }

    private suspend fun change(
        set: ApplicationChoiceSet,
        transform: (List<Record>) -> List<Record>,
    ): LocalApplicationRemovalResult {
        return locked {
            val records = read() ?: return@locked LocalApplicationRemovalResult.Failure(LocalApplicationRemovalFailure.STORAGE)
            val next = transform(records)
            if (!write(next)) return@locked LocalApplicationRemovalResult.Failure(LocalApplicationRemovalFailure.STORAGE)
            changes.tryEmit(Unit)
            LocalApplicationRemovalResult.Success(snapshot(next, set) ?: LocalApplicationMappingsSnapshot.empty())
        }
    }

    private fun snapshot(
        records: List<Record>,
        set: ApplicationChoiceSet,
    ): LocalApplicationMappingsSnapshot? {
        val mappings = records.filter { it.set == set.hex }.mapNotNull { record ->
            LocalApplicationMappingId.restore(record.id)?.let { id -> LocalApplicationMapping.restore(id, record.name) }
        }
        return LocalApplicationMappingsSnapshot.restore(mappings)
    }

    private fun read(): List<Record>? {
        return try {
            if (!Files.exists(file)) {
                emptyList()
            } else {
                Files.readAllLines(file).mapNotNull { line ->
                    line.split('\t').takeIf { it.size == FIELDS }?.let { Record(it[0], it[1], it[2], it[3]) }
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun write(records: List<Record>): Boolean {
        return try {
            Files.createDirectories(file.parent)
            val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.write(temporary, records.map { "${it.set}\t${it.key}\t${it.name}\t${it.target}" })
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun <T> locked(block: suspend () -> T): T {
        return withContext(ioDispatcher) { mutex.withLock { block() } }
    }

    private class Record(
        val set: String,
        val key: String,
        val name: String,
        val target: String,
    ) {
        val id: String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val FIELDS = 4
    }
}

private fun String.clean(): String {
    return filterNot { it == '\t' || it == '\n' || it == '\r' }
}
