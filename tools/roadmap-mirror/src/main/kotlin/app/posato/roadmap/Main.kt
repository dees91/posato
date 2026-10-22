package app.posato.roadmap

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import kotlin.system.exitProcess

class RoadmapMirror : CliktCommand(name = "roadmap-mirror") {
    override fun help(context: Context): String =
        "Keeps the public GitHub project that mirrors docs/tasks/release-roadmap.md current (\"GitHub Projects mirror\")."

    override fun run() = Unit
}

class SetStatusCommand : CliktCommand(name = "set-status") {
    override fun help(context: Context): String = "Set a row's Status, and its PR when --pr is given."

    private val row by argument("ROW-ID", help = "Roadmap row, such as SYNC-022.")
    private val status by argument("STATUS").choice(*RoadmapProject.STATUS.keys.toTypedArray())
    private val pr by option("--pr", help = "Pull request number recorded in the PR field.").int()
    private val itemId by option(
        "--item",
        help = "The row's item id (PVTI_...), as add prints it; skips GitHub's item list, which can lag or come back empty.",
    )

    override fun run() {
        val items = ProjectItems()
        val item = itemId?.also { id -> requireWritten(items.read(id), row, emptyMap()) } ?: findRow(items.list(), row).id
        val option = RoadmapProject.STATUS.getValue(status)
        items.setOption(item, RoadmapProject.STATUS_FIELD, option.id)
        pr?.let { number -> items.setText(item, RoadmapProject.PR_FIELD, "#$number") }
        val expected = mapOf(RoadmapProject.STATUS_NAME to option.name) + pr?.let { mapOf(RoadmapProject.PR_NAME to "#$it") }.orEmpty()
        val written = items.read(item)
        requireWritten(written, row, expected)
        echo("${written.title}: Status ${option.name}${pr?.let { ", PR #$it" }.orEmpty()} ($item, read back)")
    }
}

class AddCommand : CliktCommand(name = "add") {
    override fun help(context: Context): String =
        "Add a draft item for a new roadmap row, titled \"<ROW-ID>: <title>\", with its Status and PR set in the same call."

    private val row by argument("ROW-ID", help = "Roadmap row, such as QUALITY-013.")
    private val title by option("--title", help = "Short outcome after the ID.").required()
    private val body by option("--body", help = "Outcome, origin, and a link to the roadmap, in Markdown.").required()
    private val release by option("--release").choice(*RoadmapProject.RELEASE.keys.toTypedArray()).default("backlog")
    private val rowClass by option("--class").choice(*RoadmapProject.CLASS.keys.toTypedArray()).default("backlog")
    private val epic by option("--epic").choice(*RoadmapProject.EPIC.keys.toTypedArray()).required()
    private val status by option("--status").choice(*RoadmapProject.STATUS.keys.toTypedArray()).default("todo")
    private val pr by option("--pr", help = "Pull request number recorded in the PR field.").int()

    override fun run() {
        val items = ProjectItems()
        if (items.list().any { it.title.startsWith("$row: ") }) throw MirrorException("$row already has a project item.")
        val id = items.addDraft("$row: $title", body)
        val options = listOf(
            Triple(RoadmapProject.STATUS_FIELD, RoadmapProject.STATUS_NAME, RoadmapProject.STATUS.getValue(status)),
            Triple(RoadmapProject.RELEASE_FIELD, RoadmapProject.RELEASE_NAME, RoadmapProject.RELEASE.getValue(release)),
            Triple(RoadmapProject.CLASS_FIELD, RoadmapProject.CLASS_NAME, RoadmapProject.CLASS.getValue(rowClass)),
            Triple(RoadmapProject.EPIC_FIELD, RoadmapProject.EPIC_NAME, RoadmapProject.EPIC.getValue(epic)),
        )
        // The project's item list can lag a write by many minutes, so a new row gets its whole state here, not in a
        // set-status that may not find it yet (`observed` 2026-10-22).
        options.forEach { (field, _, option) -> items.setOption(id, field, option.id) }
        pr?.let { number -> items.setText(id, RoadmapProject.PR_FIELD, "#$number") }
        val expected = options.associate { (_, name, option) -> name to option.name } +
            pr?.let { mapOf(RoadmapProject.PR_NAME to "#$it") }.orEmpty()
        requireWritten(items.read(id), row, expected)
        echo("$row: $title: added as $id and read back (${expected.entries.joinToString { "${it.key} ${it.value}" }})")
    }
}

fun main(args: Array<String>) {
    try {
        RoadmapMirror().subcommands(SetStatusCommand(), AddCommand()).main(args)
    } catch (exception: MirrorException) {
        System.err.println("roadmap-mirror: ${exception.message}")
        exitProcess(1)
    }
}
