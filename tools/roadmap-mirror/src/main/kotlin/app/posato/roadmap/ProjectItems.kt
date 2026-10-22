package app.posato.roadmap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** A failure the person can act on; printed without a stack trace. */
class MirrorException(
    message: String,
) : RuntimeException(message)

/** One draft item of the project: its item id, which field edits take, its draft id, and its title. */
data class ProjectItem(
    val id: String,
    val draftId: String,
    val title: String,
)

/** An item as GitHub reads it back by its id: the project it belongs to, its title, and its field values by name. */
data class ItemState(
    val id: String,
    val projectId: String,
    val archived: Boolean,
    val title: String,
    val fields: Map<String, String>,
)

/** The new item's id in an `addProjectV2DraftIssue` response; a response without it fails. */
fun addedItemId(data: JsonObject): String = (data["addProjectV2DraftIssue"] as? JsonObject)
    ?.let { it["projectItem"] as? JsonObject }
    ?.let { (it["id"] as? JsonPrimitive)?.content }
    ?: throw MirrorException("GitHub's answer to adding the draft item names no item: $data")

/** Parses the `node` of [ProjectItems.read]; a single-select value reads as its option name, a text value as its text. */
fun parseItemState(data: JsonObject): ItemState {
    val node = data["node"] as? JsonObject ?: throw MirrorException("GitHub found no project item: $data")
    val fields = node["fieldValues"]?.jsonObject?.get("nodes")?.jsonArray.orEmpty().mapNotNull { value ->
        val entry = value.jsonObject
        val field = (entry["field"] as? JsonObject)?.let { (it["name"] as? JsonPrimitive)?.content } ?: return@mapNotNull null
        val content = (entry["name"] as? JsonPrimitive ?: entry["text"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        field to content
    }.toMap()
    return ItemState(
        id = node.getValue("id").jsonPrimitive.content,
        projectId = node["project"]?.jsonObject?.get("id")?.jsonPrimitive?.content.orEmpty(),
        archived = node["isArchived"]?.jsonPrimitive?.boolean ?: false,
        title = node["content"]?.jsonObject?.get("title")?.jsonPrimitive?.content.orEmpty(),
        fields = fields,
    )
}

/** Fails unless [item] is a live item of this project, titled `<rowId>: ...`, whose fields hold [expected]. */
fun requireWritten(
    item: ItemState,
    rowId: String,
    expected: Map<String, String>,
) {
    val problems = buildList {
        if (item.projectId != RoadmapProject.ID) add("it belongs to project '${item.projectId}'")
        if (item.archived) add("it is archived")
        if (!item.title.startsWith("$rowId: ")) add("its title is '${item.title}'")
        expected.forEach { (field, value) -> if (item.fields[field] != value) add("$field is '${item.fields[field]}', not '$value'") }
    }
    if (problems.isNotEmpty()) throw MirrorException("Item ${item.id} is not what $rowId needs: ${problems.joinToString("; ")}.")
}

/** The one item titled `<rowId>: ...`; a row ID that is a prefix of another (`SYNC-02`, `SYNC-021`) does not match. */
fun findRow(
    items: List<ProjectItem>,
    rowId: String,
): ProjectItem {
    val matches = items.filter { it.title.startsWith("$rowId: ") }
    return when (matches.size) {
        1 -> matches.single()
        0 -> throw MirrorException("No project item is titled \"$rowId: ...\". Add it with `roadmap-mirror add $rowId`.")
        else -> throw MirrorException("${matches.size} project items are titled \"$rowId: ...\"; archive the extra ones first.")
    }
}

/**
 * The project's items and field writes through `gh api graphql`. On 2026-10-22 (`observed`, gh 2.100.0) `gh project
 * item-list` returned no items for this project, and the GraphQL item list alternated between no items and a list
 * that lacked items added minutes before, while every write and a read of an item by its id worked at once.
 */
class ProjectItems {
    fun list(): List<ProjectItem> {
        val items = mutableListOf<ProjectItem>()
        var cursor: String? = null
        do {
            val after = cursor?.let { ", after: \"$it\"" }.orEmpty()
            val page = graphql(
                "query(\$project: ID!) { node(id: \$project) { ... on ProjectV2 { items(first: 100$after) { " +
                    "pageInfo { hasNextPage endCursor } nodes { id content { ... on DraftIssue { id title } } } } } } }",
                mapOf("project" to RoadmapProject.ID),
            ).getValue("node").jsonObject.getValue("items").jsonObject
            page.getValue("nodes").jsonArray.map { it.jsonObject }.forEach { node ->
                val content = node["content"] as? JsonObject ?: return@forEach
                val title = (content["title"] as? JsonPrimitive)?.content ?: return@forEach
                items += ProjectItem(node.getValue("id").jsonPrimitive.content, content.getValue("id").jsonPrimitive.content, title)
            }
            val info = page.getValue("pageInfo").jsonObject
            cursor = info.getValue("endCursor").jsonPrimitive.content.takeIf { info.getValue("hasNextPage").jsonPrimitive.boolean }
        } while (cursor != null)
        // The project always holds items; an empty answer is GitHub's listing failing, not a missing row.
        if (items.isEmpty()) throw MirrorException("GitHub listed no items for the project; its item list is unavailable now, so retry later.")
        return items
    }

    /** Adds a draft item and returns its item id. */
    fun addDraft(
        title: String,
        body: String,
    ): String = addedItemId(
        graphql(
            "mutation(\$project: ID!, \$title: String!, \$body: String!) { addProjectV2DraftIssue(input: " +
                "{projectId: \$project, title: \$title, body: \$body}) { projectItem { id } } }",
            mapOf("project" to RoadmapProject.ID, "title" to title, "body" to body),
        ),
    )

    /** Reads one item by its id, which does not depend on the project's item list. */
    fun read(item: String): ItemState = parseItemState(
        graphql(
            "query(\$item: ID!) { node(id: \$item) { ... on ProjectV2Item { id isArchived project { id } " +
                "content { ... on DraftIssue { title } } fieldValues(first: 30) { nodes { " +
                "... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2SingleSelectField { name } } } " +
                "... on ProjectV2ItemFieldTextValue { text field { ... on ProjectV2Field { name } } } } } } } }",
            mapOf("item" to item),
        ),
    )

    fun setOption(
        item: String,
        field: String,
        option: String,
    ) {
        setValue(item, field, "singleSelectOptionId", option)
    }

    fun setText(
        item: String,
        field: String,
        text: String,
    ) {
        setValue(item, field, "text", text)
    }

    private fun setValue(
        item: String,
        field: String,
        kind: String,
        value: String,
    ) {
        graphql(
            "mutation(\$project: ID!, \$item: ID!, \$field: ID!, \$value: String!) { updateProjectV2ItemFieldValue(input: " +
                "{projectId: \$project, itemId: \$item, fieldId: \$field, value: {$kind: \$value}}) { projectV2Item { id } } }",
            mapOf("project" to RoadmapProject.ID, "item" to item, "field" to field, "value" to value),
        )
    }

    private fun graphql(
        query: String,
        variables: Map<String, String>,
    ): JsonObject {
        val command = listOf("gh", "api", "graphql", "-f", "query=$query") + variables.flatMap { (name, value) -> listOf("-f", "$name=$value") }
        // Both streams go to files, so neither pipe can fill and block gh, and the timeout bounds a hung call.
        val outputFile = Files.createTempFile("roadmap-mirror", ".json")
        val errorFile = Files.createTempFile("roadmap-mirror", ".err")
        try {
            val process = ProcessBuilder(command).redirectOutput(outputFile.toFile()).redirectError(errorFile.toFile()).start()
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw MirrorException("gh api graphql did not finish within $TIMEOUT_SECONDS s.")
            }
            val output = Files.readString(outputFile)
            if (process.exitValue() != 0) throw MirrorException("gh api graphql failed: ${Files.readString(errorFile).ifBlank { output }.trim()}")
            return parse(output)
        } finally {
            Files.deleteIfExists(outputFile)
            Files.deleteIfExists(errorFile)
        }
    }

    private fun parse(output: String): JsonObject {
        val response = Json.parseToJsonElement(output).jsonObject
        response["errors"]?.let { throw MirrorException("GitHub refused the request: $it") }
        return response.getValue("data").jsonObject
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L
    }
}
