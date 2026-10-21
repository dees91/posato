package app.posato.feature.transfer

import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.transfer.data.PauseSetFileJson
import app.posato.feature.transfer.data.PauseSetFileProblem
import app.posato.feature.transfer.data.PauseSetJsonResult
import app.posato.feature.transfer.domain.FileSchedule
import app.posato.feature.transfer.domain.FileSet
import app.posato.feature.transfer.domain.PauseSetFile
import app.posato.feature.transfer.domain.PauseSetFileLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The plain export format. Files come from other devices and other people, so a reader must refuse every
 * malformed or oversized file with its own reason and never crash, while a written file reads back the same.
 */
class PauseSetFileJsonTest {
    @Test
    fun `given sets an unnamed first set a default and schedules when written then they read back the same`() {
        val file = PauseSetFile(
            sets = listOf(
                FileSet("s1", null, first = true, domains = domains("example.com", "example.net")),
                FileSet("s2", "Evening", first = false, domains = domains("example.net")),
            ),
            defaultSet = "s2",
            schedules = listOf(FileSchedule("Deep work", weekdays = 0b0011111, startMinute = 540, endMinute = 660, set = "s1")),
        )

        assertEquals(PauseSetJsonResult.Read(file), PauseSetFileJson.decode(PauseSetFileJson.encode(file)))
    }

    @Test
    fun `given the largest file Posato allows when written then it stays under the size cap and reads back`() {
        val longest = (0 until 1_024).map(::longestDomain)
        val file = PauseSetFile(
            sets = (1..10).map { index -> FileSet("s$index", "Set $index ".padEnd(80, 'x'), first = false, domains = longest) },
            defaultSet = null,
            schedules = (1..10).map { index -> FileSchedule("Plan $index ".padEnd(80, 'x'), 0b1111111, 0, 1439, "s1") },
        )

        val bytes = PauseSetFileJson.encode(file)

        assertTrue(bytes.size < PauseSetFileLimits.MAX_FILE_BYTES, "${bytes.size} bytes")
        assertEquals(PauseSetJsonResult.Read(file), PauseSetFileJson.decode(bytes))
    }

    @Test
    fun `given names with spaces around them when read then they are trimmed`() {
        val result = PauseSetFileJson.decode(json(sets = """[{"ref":"a","name":"  Focus ","first":false,"domains":[]}]"""))

        assertEquals("Focus", assertIs<PauseSetJsonResult.Read>(result).file.sets.single().name)
    }

    @Test
    fun `given unknown fields at several levels when read then they are ignored`() {
        val sets = """[{"ref":"a","name":"Focus","first":false,"domains":["example.com"],"color":{"r":[1,2,{"x":null}]}}]"""

        val result = PauseSetFileJson.decode(json(sets = sets, extra = ""","note":{"a":{"b":[true]}}"""))

        assertEquals(listOf("example.com"), assertIs<PauseSetJsonResult.Read>(result).file.sets.single().domains.map(ExactDomain::canonicalValue))
    }

    @Test
    fun `given an unknown field nested deeper than the format allows when read then the file is refused without parsing it`() {
        val deep = "[".repeat(200_000) + "]".repeat(200_000)

        assertRefused(PauseSetFileProblem.NOT_A_POSATO_FILE, json(extra = ""","deep":$deep"""))
    }

    @Test
    fun `given files that are not Posato exports when read then each is refused as not a Posato file`() {
        assertRefused(PauseSetFileProblem.NOT_A_POSATO_FILE, "not json".encodeToByteArray())
        assertRefused(PauseSetFileProblem.NOT_A_POSATO_FILE, "[]".encodeToByteArray())
        assertRefused(PauseSetFileProblem.NOT_A_POSATO_FILE, """{"format":"other","version":1}""".encodeToByteArray())
        assertRefused(PauseSetFileProblem.NOT_A_POSATO_FILE, byteArrayOf(0xC3.toByte(), 0x28))
        assertRefused(PauseSetFileProblem.NOT_A_POSATO_FILE, ByteArray(PauseSetFileLimits.MAX_FILE_BYTES + 1) { ' '.code.toByte() })
    }

    @Test
    fun `given a file from a newer Posato when read then it is refused as newer`() {
        assertRefused(PauseSetFileProblem.NEWER_VERSION, json(version = 2))
    }

    @Test
    fun `given more sets or schedules than Posato keeps when read then the file is over the limit`() {
        val sets = (1..11).joinToString(",", "[", "]") { """{"ref":"s$it","name":"Set $it","first":false,"domains":[]}""" }
        val schedules = (1..11).joinToString(",", "[", "]") { """{"name":"P$it","weekdays":1,"start":0,"end":60,"set":"a"}""" }

        assertRefused(PauseSetFileProblem.OVER_LIMIT, json(sets = sets))
        assertRefused(PauseSetFileProblem.OVER_LIMIT, json(sets = """[{"ref":"a","name":"A","first":false,"domains":[]}]""", schedules = schedules))
    }

    @Test
    fun `given contents that break the format's rules when read then each is refused as invalid`() {
        val set = """{"ref":"a","name":"A","first":false,"domains":[]}"""
        listOf(
            json(sets = """[{"ref":"a","name":"A","first":false,"domains":["not a domain"]}]"""),
            json(sets = """[{"ref":"a","name":null,"first":false,"domains":[]}]"""),
            json(sets = """[{"ref":"a","name":"","first":false,"domains":[]}]"""),
            json(sets = """[{"ref":"a","name":"A","first":"no","domains":[]}]"""),
            json(sets = "[$set,$set]"),
            json(sets = "[$set]", schedules = """[{"name":"P","weekdays":1,"start":0,"end":60,"set":"missing"}]"""),
            json(sets = "[$set]", schedules = """[{"name":"P","weekdays":1,"start":0,"end":10,"set":"a"}]"""),
            json(sets = "[$set]", schedules = """[{"name":"P","weekdays":0,"start":0,"end":60,"set":"a"}]"""),
            json(sets = "[$set]", schedules = """[{"name":"P","weekdays":1,"start":"09:00","end":60,"set":"a"}]"""),
            json(sets = "[$set]", extra = ""","defaultSet":"missing""""),
        ).forEach { file -> assertRefused(PauseSetFileProblem.INVALID_CONTENTS, file) }
    }

    private fun assertRefused(
        problem: PauseSetFileProblem,
        bytes: ByteArray,
    ) {
        assertEquals(PauseSetJsonResult.Refused(problem), PauseSetFileJson.decode(bytes))
    }
}

private fun json(
    version: Int = 1,
    sets: String = "[]",
    schedules: String = "[]",
    extra: String = "",
): ByteArray {
    return """{"format":"posato-pause-sets","version":$version,"sets":$sets,"schedules":$schedules$extra}""".encodeToByteArray()
}

internal fun domains(vararg values: String): List<ExactDomain> {
    return values.map { value -> checkNotNull(ExactDomain.restore(value)) }
}

/** A distinct domain of the longest length Posato keeps, 253 characters. */
internal fun longestDomain(index: Int): ExactDomain {
    val first = index.toString().padStart(4, '0') + "a".repeat(59)
    return checkNotNull(ExactDomain.restore("$first.${"b".repeat(63)}.${"c".repeat(63)}.${"d".repeat(61)}"))
}
