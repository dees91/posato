package app.posato.roadmap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * On 2026-10-22 `add` reported success while GitHub's item list showed neither new row, so a write is now proved by
 * reading the item back by its id. A response without the new item's id, or a read whose fields differ from what was
 * written, must fail the command instead of printing success.
 */
class WriteCheckTest {
    @Test
    fun `given an add response when reading the new item id then a missing item fails`() {
        val added = Json.parseToJsonElement("""{"addProjectV2DraftIssue": {"projectItem": {"id": "PVTI_1"}}}""").jsonObject
        assertEquals("PVTI_1", addedItemId(added))
        val empty = Json.parseToJsonElement("""{"addProjectV2DraftIssue": {"projectItem": null}}""").jsonObject
        assertFailsWith<MirrorException> { addedItemId(empty) }
    }

    @Test
    fun `given an item read back when checking a write then another project or a differing field fails`() {
        val node = Json.parseToJsonElement(
            """{"node": {"id": "PVTI_1", "isArchived": false, "project": {"id": "${RoadmapProject.ID}"},
            "content": {"title": "QUALITY-013: Linux onboarding"},
            "fieldValues": {"nodes": [
              {"name": "Todo", "field": {"name": "Status"}},
              {"text": "#191", "field": {"name": "PR"}},
              {"text": "QUALITY-013: Linux onboarding", "field": {"name": "Title"}},
              {}
            ]}}}""",
        ).jsonObject
        val item = parseItemState(node)
        assertEquals(mapOf("Status" to "Todo", "PR" to "#191", "Title" to "QUALITY-013: Linux onboarding"), item.fields)
        requireWritten(item, "QUALITY-013", mapOf("Status" to "Todo", "PR" to "#191"))
        assertFailsWith<MirrorException> { requireWritten(item, "QUALITY-013", mapOf("Status" to "Done")) }
        assertFailsWith<MirrorException> { requireWritten(item.copy(projectId = "PVT_other"), "QUALITY-013", emptyMap()) }
        assertFailsWith<MirrorException> { requireWritten(item.copy(archived = true), "QUALITY-013", emptyMap()) }
    }
}
