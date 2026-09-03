package app.posato.desktop.mappings

import app.posato.desktop.macos.MacOsApplicationPicker
import app.posato.desktop.macos.MacOsApplicationPickerResult
import app.posato.desktop.macos.SelectedMacOsApplication
import app.posato.feature.targets.data.LocalApplicationMappingDisplay
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationRemovalResult
import app.posato.feature.targets.data.LocalApplicationSelectionRejection
import app.posato.feature.targets.data.LocalApplicationSelectionResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.sql.DriverManager
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopLocalApplicationMappingsTest {
    @Test
    fun `selection persists across reopen and removal is durable`() {
        withStore(MacOsApplicationPickerResult.Success(listOf(application("Browser", 1)))) { store, path ->
            val selected = assertIs<LocalApplicationSelectionResult.Success>(runBlocking { store.chooseApplications() })
            assertEquals(
                listOf("Browser"),
                selected.snapshot.mappings.map { mapping -> (mapping.display as LocalApplicationMappingDisplay.Named).value },
            )
            val id = selected.snapshot.mappings.single().id
            store.close()

            val reopened = createStore(path, MacOsApplicationPickerResult.Cancelled)
            val loaded = assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { reopened.load() })
            assertEquals(selected.snapshot, loaded.snapshot)
            assertIs<LocalApplicationRemovalResult.Success>(runBlocking { reopened.remove(id) })
            reopened.close()

            val empty = createStore(path, MacOsApplicationPickerResult.Cancelled)
            assertTrue(assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { empty.load() }).snapshot.mappings.isEmpty())
            empty.close()
        }
    }

    @Test
    fun `invalid batch and over-capacity batch preserve prior snapshot`() {
        withStore(MacOsApplicationPickerResult.Success(listOf(application("Existing", 1)))) { store, path ->
            assertIs<LocalApplicationSelectionResult.Success>(runBlocking { store.chooseApplications() })
            store.close()

            val invalid = createStore(
                path,
                MacOsApplicationPickerResult.Success(listOf(application("Valid", 2), SelectedMacOsApplication(" Invalid ", byteArrayOf(3)))),
            )
            assertIs<LocalApplicationSelectionResult.Failure>(runBlocking { invalid.chooseApplications() })
            assertEquals(1, assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { invalid.load() }).snapshot.mappings.size)
            invalid.close()

            val tooMany = createStore(
                path,
                MacOsApplicationPickerResult.Success((2..65).map { value -> application("App $value", value) }),
            )
            val rejection = assertIs<LocalApplicationSelectionResult.Rejected>(runBlocking { tooMany.chooseApplications() })
            assertEquals(LocalApplicationSelectionRejection.CAPACITY, rejection.reason)
            assertEquals(1, assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { tooMany.load() }).snapshot.mappings.size)
            tooMany.close()
        }
    }

    @Test
    fun `database operations and picker run on injected dispatcher`() {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "mapping-io") }
        executor.asCoroutineDispatcher().use { dispatcher ->
            var pickerThread = ""
            val picker = MacOsApplicationPicker {
                pickerThread = Thread.currentThread().name
                MacOsApplicationPickerResult.Cancelled
            }
            val root = createTempDirectory("posato-mappings")
            try {
                val store = createStore(root.resolve("mappings.db"), picker, dispatcher)
                runBlocking { store.chooseApplications() }
                assertTrue(pickerThread.startsWith("mapping-io"))
                store.close()
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `database directory and artifacts are owner only`() {
        withStore(MacOsApplicationPickerResult.Success(listOf(application("Browser", 1)))) { store, path ->
            runBlocking { store.chooseApplications() }
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path.parent)))
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)))
            listOf("-journal", "-wal", "-shm").forEach { suffix ->
                val sidecar = path.resolveSibling("${path.fileName}$suffix")
                if (Files.exists(sidecar)) {
                    assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(sidecar)))
                }
            }
        }
    }

    @Test
    fun `corrupted persisted identity fails closed without resetting the database`() {
        withStore(MacOsApplicationPickerResult.Success(listOf(application("Browser", 1)))) { store, path ->
            assertIs<LocalApplicationSelectionResult.Success>(runBlocking { store.chooseApplications() })
            store.close()
            DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
                connection.prepareStatement("UPDATE localApplicationMapping SET mappingId = ?").use { statement ->
                    statement.setBytes(1, ByteArray(32))
                    statement.executeUpdate()
                }
            }

            val reopened = createStore(path, MacOsApplicationPickerResult.Cancelled)
            val failure = assertIs<LocalApplicationMappingsLoadResult.Failure>(runBlocking { reopened.load() })
            assertEquals(LocalApplicationMappingsLoadFailure.CORRUPTION, failure.reason)
            assertTrue(Files.size(path) > 0)
            reopened.close()
        }
    }

    @Test
    fun `filesystem failure during choose and remove rolls back durable changes`() {
        val root = createTempDirectory("posato-mappings")
        val path = root.resolve("data/mappings.db")
        val owner = Files.getOwner(root)
        val files = FailingApplicationMappingFiles(MacOsApplicationMappingFiles(path, owner))
        try {
            val chooser = createStore(
                files,
                MacOsApplicationPicker { MacOsApplicationPickerResult.Success(listOf(application("Browser", 1))) },
            )
            assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { chooser.load() })
            files.failAfterSuccessfulSecureCalls = 1
            assertIs<LocalApplicationSelectionResult.Failure>(runBlocking { chooser.chooseApplications() })
            chooser.close()

            val afterChooseFailure = createStore(path, MacOsApplicationPickerResult.Success(listOf(application("Browser", 1))))
            assertTrue(
                assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { afterChooseFailure.load() })
                    .snapshot.mappings.isEmpty(),
            )
            val selected = assertIs<LocalApplicationSelectionResult.Success>(runBlocking { afterChooseFailure.chooseApplications() })
            val id = selected.snapshot.mappings.single().id
            afterChooseFailure.close()

            val removalFiles = FailingApplicationMappingFiles(MacOsApplicationMappingFiles(path, owner))
            val remover = createStore(removalFiles, MacOsApplicationPicker { MacOsApplicationPickerResult.Cancelled })
            assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { remover.load() })
            removalFiles.failAfterSuccessfulSecureCalls = 1
            assertIs<LocalApplicationRemovalResult.Failure>(runBlocking { remover.remove(id) })
            remover.close()

            val reopened = createStore(path, MacOsApplicationPickerResult.Cancelled)
            assertEquals(1, assertIs<LocalApplicationMappingsLoadResult.Success>(runBlocking { reopened.load() }).snapshot.mappings.size)
            reopened.close()
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun withStore(
        pickerResult: MacOsApplicationPickerResult,
        block: (DesktopLocalApplicationMappings, java.nio.file.Path) -> Unit,
    ) {
        val root = createTempDirectory("posato-mappings")
        val path = root.resolve("data/mappings.db")
        try {
            createStore(path, pickerResult).use { store -> block(store, path) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun createStore(
        path: java.nio.file.Path,
        pickerResult: MacOsApplicationPickerResult,
    ): DesktopLocalApplicationMappings {
        return createStore(path, MacOsApplicationPicker { pickerResult }, kotlinx.coroutines.Dispatchers.IO)
    }

    private fun createStore(
        path: java.nio.file.Path,
        picker: MacOsApplicationPicker,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): DesktopLocalApplicationMappings {
        val owner = Files.getOwner(path.parent?.takeIf(Files::exists) ?: path.parent.parent)
        return DesktopLocalApplicationMappings(MacOsApplicationMappingFiles(path, owner), picker, dispatcher)
    }

    private fun createStore(
        files: ApplicationMappingFiles,
        picker: MacOsApplicationPicker,
    ): DesktopLocalApplicationMappings {
        return DesktopLocalApplicationMappings(files, picker, kotlinx.coroutines.Dispatchers.IO)
    }

    private fun application(
        name: String,
        value: Int
    ): SelectedMacOsApplication {
        return SelectedMacOsApplication(name, byteArrayOf(value.toByte()))
    }
}

private class FailingApplicationMappingFiles(
    private val delegate: ApplicationMappingFiles,
) : ApplicationMappingFiles {
    var failAfterSuccessfulSecureCalls: Int? = null

    override val path: java.nio.file.Path
        get() = delegate.path

    override fun prepare() {
        delegate.prepare()
    }

    override fun secureDatabaseArtifacts() {
        val remainingCalls = failAfterSuccessfulSecureCalls
        if (remainingCalls == 0) {
            failAfterSuccessfulSecureCalls = null
            throw IllegalStateException("synthetic permission failure")
        }
        if (remainingCalls != null) {
            failAfterSuccessfulSecureCalls = remainingCalls - 1
        }
        delegate.secureDatabaseArtifacts()
    }
}
