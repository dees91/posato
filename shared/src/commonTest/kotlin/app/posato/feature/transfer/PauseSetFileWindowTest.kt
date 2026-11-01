package app.posato.feature.transfer

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.WebsiteWindow
import app.posato.feature.transfer.data.PauseSetFileJson
import app.posato.feature.transfer.data.PauseSetJsonResult
import app.posato.feature.transfer.domain.FileSet
import app.posato.feature.transfer.domain.ImportCurrent
import app.posato.feature.transfer.domain.ImportIds
import app.posato.feature.transfer.domain.ImportMode
import app.posato.feature.transfer.domain.ImportPlanResult
import app.posato.feature.transfer.domain.PauseSetFile
import app.posato.feature.transfer.domain.exportFile
import app.posato.feature.transfer.domain.planImport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Window rules in the pause set file (product rules, Export and import). Failure modes: a rule is lost on the way,
 * an invalid entry refuses a whole file or imports a rule outside the fixed choices, and an import overwrites a
 * rule the person kept here.
 */
class PauseSetFileWindowTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(10)))
    private val imported = checkNotNull(PauseSetId.of(testIdentifier(20)))
    private val news = domain("news.example")
    private val mail = domain("mail.example")
    private val hourly = checkNotNull(WebsiteWindow.of(60, 5))
    private val halfHourly = checkNotNull(WebsiteWindow.of(30, 15))

    @Test
    fun `given a set with rules when exported and read back then the rules travel beside plain website names`() {
        val sets = checkNotNull(PauseSets.of(listOf(LocalPauseSet(work, "Work", listOf(mail, news), windows = mapOf(news to hourly))), null))
        val file = exportFile(sets, emptyList())
        val bytes = PauseSetFileJson.encode(file)
        val written = checkNotNull(Json.parseToJsonElement(bytes.decodeToString()).jsonObject["sets"]).jsonArray.single().jsonObject

        assertEquals(mapOf(news to hourly), file.sets.single().windows)
        assertEquals(file, assertIs<PauseSetJsonResult.Read>(PauseSetFileJson.decode(bytes)).file)
        assertEquals(listOf("mail.example", "news.example"), checkNotNull(written["domains"]).jsonArray.map { it.jsonPrimitive.content }, "plain names")
    }

    @Test
    fun `given invalid rule entries when read then each is dropped and its website imports without a rule`() {
        val bytes = (
            """{"format":"posato-pause-sets","version":1,"schedules":[],"sets":[{"ref":"s1","name":"Work","first":false,""" +
                """"domains":["mail.example","news.example"],"windows":{"news.example":{"period":60,"length":5},""" +
                """"mail.example":{"period":45,"length":5},"other.example":{"period":30,"length":5},"NEWS.example":{"period":30,"length":5}}}]}"""
            ).encodeToByteArray()
        val oddField = (
            """{"format":"posato-pause-sets","version":1,"schedules":[],"sets":[{"ref":"s1","name":"Work","first":false,""" +
                """"domains":["news.example"],"windows":["news.example"]}]}"""
            ).encodeToByteArray()

        assertEquals(mapOf(news to hourly), assertIs<PauseSetJsonResult.Read>(PauseSetFileJson.decode(bytes)).file.sets.single().windows)
        assertEquals(emptyMap(), assertIs<PauseSetJsonResult.Read>(PauseSetFileJson.decode(oddField)).file.sets.single().windows)
    }

    @Test
    fun `given each import mode when a file carries rules then replace and separate take them and combine keeps the rules here`() {
        val here = checkNotNull(PauseSets.of(listOf(LocalPauseSet(work, "Work", listOf(news), windows = mapOf(news to halfHourly))), null))
        val file = PauseSetFile(
            listOf(FileSet("s1", "Work", false, listOf(mail, news), windows = mapOf(news to hourly, mail to hourly))),
            null,
            emptyList(),
        )
        val ids = ImportIds(mapOf("s1" to imported), emptyList())
        val current = ImportCurrent(here, emptyList(), emptySet(), emptySet())

        fun windowsAfter(mode: ImportMode, setId: PauseSetId): Map<ExactDomain, WebsiteWindow> {
            val plan = assertIs<ImportPlanResult.Planned>(planImport(current, file, mode, ids)).plan
            return plan.sets.sets.single { it.id == setId }.windows
        }

        assertEquals(mapOf(news to halfHourly, mail to hourly), windowsAfter(ImportMode.COMBINE, work))
        assertEquals(mapOf(news to hourly, mail to hourly), windowsAfter(ImportMode.SEPARATE, imported))
        assertEquals(mapOf(news to hourly, mail to hourly), windowsAfter(ImportMode.REPLACE, imported))
    }

    private fun domain(value: String): ExactDomain {
        return checkNotNull(ExactDomain.restore(value))
    }
}
