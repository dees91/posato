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

    override fun run() {
        val items = ProjectItems()
        val item = findRow(items.list(), row)
        items.setOption(item.id, RoadmapProject.STATUS_FIELD, RoadmapProject.STATUS.getValue(status))
        pr?.let { number -> items.setText(item.id, RoadmapProject.PR_FIELD, "#$number") }
        echo("${item.title}: Status $status${pr?.let { ", PR #$it" }.orEmpty()}")
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
        // The project's item list can lag a write by many minutes, so a new row gets its whole state here, not in a
        // set-status that may not find it yet (`observed` 2026-10-22).
        items.setOption(id, RoadmapProject.STATUS_FIELD, RoadmapProject.STATUS.getValue(status))
        pr?.let { number -> items.setText(id, RoadmapProject.PR_FIELD, "#$number") }
        items.setOption(id, RoadmapProject.RELEASE_FIELD, RoadmapProject.RELEASE.getValue(release))
        items.setOption(id, RoadmapProject.CLASS_FIELD, RoadmapProject.CLASS.getValue(rowClass))
        items.setOption(id, RoadmapProject.EPIC_FIELD, RoadmapProject.EPIC.getValue(epic))
        echo("$row: $title: added to $release as $id, Status $status${pr?.let { ", PR #$it" }.orEmpty()}")
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
