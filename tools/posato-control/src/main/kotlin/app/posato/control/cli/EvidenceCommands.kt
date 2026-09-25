package app.posato.control.cli

import app.posato.control.backend.LogsResult
import app.posato.control.backend.ResetPlan
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ScreenshotCommand :
    ControlCommand("screenshot", "Capture the application window (desktop), the simulator screen, or a device screenshot through the driver.") {
    private val name by option("--name", help = "Artifact name without extension.").default("screenshot")
    private val out by option("--out", help = "Write the PNG to this path instead of the run directory.").path()
    private val process by ProcessOptions()

    override fun execute(session: Session): JsonElement {
        val path = session.backend(process.selector()).screenshot(name, out)
        return buildJsonObject { put("path", session.layout.relativize(path)) }
    }
}

class LogsCommand : ControlCommand("logs", "Read the captured application log, or stream the simulator log for a few seconds.") {
    private val tail by option("--tail", help = "Number of trailing lines to return.").int().default(DEFAULT_TAIL)
    private val streamSeconds by option("--stream-seconds", help = "Simulator only: stream the unified log for this many seconds.").int()

    override fun execute(session: Session): JsonElement =
        ControlJson.pretty.encodeToJsonElement(LogsResult.serializer(), session.backend().logs(tail, streamSeconds))

    private companion object {
        const val DEFAULT_TAIL = 200
    }
}

class DbCommand : CliktCommand(name = "db") {
    override fun help(context: Context): String = "Inspect the application's local SQLite databases (desktop and simulator)."

    override fun run() = Unit
}

class DbPathCommand : ControlCommand("path", "Print the local database paths for the target.") {
    override fun execute(session: Session): JsonElement =
        ControlJson.pretty.encodeToJsonElement(ListSerializer(String.serializer()), session.backend().databasePaths().map { it.toString() })
}

@Serializable
data class DbQueryResult(
    val database: String,
    val rows: JsonElement,
)

class DbQueryCommand : ControlCommand("query", "Run a read-only SQL statement against the policy database and return JSON rows.") {
    private val sql by option("--sql", help = "SQL to run, e.g. \"select count(*) from exact_domain_policy\".").required()
    private val database by option("--database", help = "Database file name when the target has several (default: the first).")

    override fun execute(session: Session): JsonElement {
        val databases = session.backend().databasePaths()
        val chosen = database?.let { name -> databases.firstOrNull { it.fileName.toString() == name } } ?: databases.first()
        if (!chosen.toFile().isFile) {
            throw ControlException(
                ErrorCode.APP_NOT_INSTALLED,
                "The database $chosen does not exist yet.",
                "Launch the application once so it creates its database.",
            )
        }
        val output = session.context.subprocess.run(listOf("/usr/bin/sqlite3", "-readonly", "-json", chosen.toString(), sql))
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Running the SQL statement")
        val rows = if (output.stdout.isBlank()) {
            JsonPrimitive("[]").let {
                ControlJson.lenient.parseToJsonElement("[]")
            }
        } else {
            ControlJson.lenient.parseToJsonElement(output.stdout)
        }
        return ControlJson.pretty.encodeToJsonElement(DbQueryResult.serializer(), DbQueryResult(chosen.toString(), rows))
    }
}

class ResetCommand :
    ControlCommand(
        "reset",
        "Delete the application's local state (desktop, simulator) or uninstall it (device). Backs up deleted files into the run directory.",
    ) {
    private val dryRun by option("--dry-run", help = "List what would be deleted without deleting.").flag()
    private val yes by option("--yes", help = "Confirm the deletion.").flag()
    private val keepInstall by option("--keep-install", help = "Simulator: delete the database files but keep the app installed.").flag()

    override fun execute(session: Session): JsonElement {
        if (!dryRun && !yes) {
            val plan = session.backend().reset(dryRun = true, keepInstall = keepInstall)
            throw ControlException(
                ErrorCode.REFUSED_WITHOUT_CONFIRMATION,
                "Refusing to delete state without --yes. Planned: ${plan.deletions.size} file(s)" +
                    (if (plan.uninstall) " and an uninstall." else "."),
                "Rerun with --dry-run to list the paths, then with --yes to confirm.",
            )
        }
        return ControlJson.pretty.encodeToJsonElement(ResetPlan.serializer(), session.backend().reset(dryRun, keepInstall))
    }
}

class CleanupCommand : ControlCommand("cleanup", "Stop processes this tool started and optionally purge derived data. Never deletes run evidence.") {
    private val dryRun by option("--dry-run", help = "List the actions without performing them.").flag()
    private val purgeDerivedData by option("--purge-derived-data", help = "Also delete the persistent DerivedData directories for the target.").flag()

    override fun execute(session: Session): JsonElement =
        ControlJson.pretty.encodeToJsonElement(ListSerializer(String.serializer()), session.backend().cleanup(dryRun, purgeDerivedData))
}

class ArtifactsCommand : ControlCommand("artifacts", "Print the run directory layout and the latest run.") {
    override val hostDesktopAllowed = true

    override fun execute(session: Session): JsonElement = buildJsonObject {
        put("runsDirectory", session.layout.relativize(session.layout.runsDirectory))
        put("latest", session.layout.relativize(session.layout.latestRunLink))
        put("stateFile", session.layout.relativize(session.layout.stateFile))
        put("currentRun", session.layout.relativize(session.context.runDirectory))
    }
}
