package app.posato.feature.targets.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A duplicated set on Android or Linux keeps this device's apps: the copy holds the source's choices only. */
class CatalogApplicationMappingsCopyTest {
    private val work = checkNotNull(ApplicationChoiceSet.restore("00000000000040008000000000000050"))

    @Test
    fun `copying a set's choices gives the other set the same apps and replaces what it had`() = runBlocking {
        val root = createTempDirectory("posato-catalog")
        try {
            val catalog = PickingCatalog()
            val mappings = CatalogApplicationMappings(root.resolve("choices.tsv"), catalog, Dispatchers.IO)
            catalog.next = listOf("browser", "editor")
            assertIs<LocalApplicationSelectionResult.Success>(mappings.chooseApplications(work))
            catalog.next = listOf("chat")
            assertIs<LocalApplicationSelectionResult.Success>(mappings.chooseApplications(ApplicationChoiceSet.FIRST))

            assertIs<LocalApplicationRemovalResult.Success>(mappings.copyChoices(work, ApplicationChoiceSet.FIRST))

            assertEquals(listOf("browser", "editor"), mappings.names(ApplicationChoiceSet.FIRST))
            assertEquals(listOf("browser", "editor"), mappings.names(work))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private suspend fun CatalogApplicationMappings.names(set: ApplicationChoiceSet): List<String> {
        val snapshot = assertIs<LocalApplicationMappingsLoadResult.Success>(load(set)).snapshot
        return snapshot.mappings.map { mapping -> (mapping.display as LocalApplicationMappingDisplay.Named).value }.sorted()
    }

    private class PickingCatalog : ApplicationCatalog {
        var next: List<String> = emptyList()

        override suspend fun installed(): List<CatalogEntry> {
            return listOf("browser", "editor", "chat").map { key -> CatalogEntry(key, key, "/usr/bin/$key") }
        }

        override suspend fun choose(
            entries: List<CatalogEntry>,
            chosen: Set<String>,
        ): List<CatalogEntry> = entries.filter { entry -> entry.key in next }
    }
}
