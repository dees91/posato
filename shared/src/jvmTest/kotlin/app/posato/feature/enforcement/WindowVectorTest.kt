package app.posato.feature.enforcement

import app.posato.feature.schedules.JavaScheduleZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cross-target window vectors (ADR 0006 TARGETS-013 amendment, "One calculation, shared vectors"): named
 * zones across daylight saving changes, segments, the open state, the next boundary, and the next opening or
 * close. The Mac helper, the iPhone extension, and the JVM helpers read the same file.
 */
class WindowVectorTest {
    @Test
    fun `given the shared vectors when the shared calculator evaluates them then every case matches`() {
        val cases = Json.parseToJsonElement(File(VECTORS).readText()).jsonObject.getValue("cases").jsonArray

        assertTrue(cases.size >= 20)
        cases.map { it.jsonObject }.forEach { case ->
            val name = case.getValue("name").jsonPrimitive.content
            val zone = JavaScheduleZone { ZoneId.of(case.getValue("zone").jsonPrimitive.content) }
            val at = case.getValue("at").jsonPrimitive.long
            val plan = DomainWindowPlan("vector.example", case.getValue("plan").jsonArray.map { it.jsonObject.segment() })
            val status = (case["status"] as? JsonObject)?.let { expected ->
                val instant = expected.getValue("at").jsonPrimitive.long
                if (expected.getValue("kind").jsonPrimitive.content == "openUntil") WindowStatus.OpenUntil(instant) else WindowStatus.OpensAt(instant)
            }

            assertEquals(case.getValue("open").jsonPrimitive.boolean, plan.isOpenAt(at, zone), "$name: open")
            assertEquals(case.getValue("nextBoundary").jsonPrimitive.longOrNull, plan.nextBoundary(at, zone), "$name: next boundary")
            assertEquals(status, plan.status(at, zone), "$name: status")
        }
    }

    private fun JsonObject.segment(): WindowSegment {
        return WindowSegment(getValue("until").jsonPrimitive.long, getValue("period").jsonPrimitive.int, getValue("length").jsonPrimitive.int)
    }

    private companion object {
        /** Relative to the shared module, where Gradle runs its tests. */
        const val VECTORS: String = "src/commonTest/resources/website-window-vectors.json"
    }
}
