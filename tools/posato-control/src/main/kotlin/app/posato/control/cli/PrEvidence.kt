package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.io.path.exists

/** One `Verified <sha> on <target>: ... run <run-id>` comment: the commit it names and the runs it cites. */
data class VerifiedLine(
    val commit: String,
    val runIds: List<String>,
)

private val VERIFIED = Regex("""\bVerified\s+([0-9a-f]{7,40})\b""")
private val RUN_ID = Regex("""\b(\d{8}-\d{6}-[0-9a-f]{4})\b""")

/** The verification lines among a pull request's comments, in their order. */
internal fun parseVerifiedLines(comments: List<String>): List<VerifiedLine> = comments.flatMap { comment ->
    comment.lines().mapNotNull { line ->
        VERIFIED.find(line)?.let { match -> VerifiedLine(match.groupValues[1], RUN_ID.findAll(line).map { it.groupValues[1] }.toList()) }
    }
}

/**
 * The paths whose change after the verified commit invalidates the evidence: the application sources and their
 * builds. Documentation, wiki, skills, and the verification tooling may change after the run.
 */
internal fun productChanges(paths: List<String>): List<String> = paths.filter { path ->
    PRODUCT_PREFIXES.any { path.startsWith(it) } || path in PRODUCT_FILES
}

private val PRODUCT_PREFIXES = listOf(
    "shared/",
    "desktopApp/",
    "iosApp/",
    "macosHelper/",
    "macosSyncCompanion/",
    "buildSrc/",
    "gradle/",
)

private val PRODUCT_FILES = setOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "Version.xcconfig")

/**
 * Checks a pull request's verification evidence before it is marked ready: its last `Verified <sha>` comment must name
 * a commit whose product code equals the head, and every run any `Verified` comment cites must exist under this
 * worktree's run directory. Every completed-change review of release 1.4 stopped on one of these by hand.
 */
class PrEvidenceCommand :
    ControlCommand(
        "pr-evidence",
        "Check that a pull request's Verified comments name the head's product code and cite runs that exist here.",
    ) {
    private val pr by option("--pr", help = "Pull request number.").int().required()

    override fun execute(session: Session): JsonElement {
        val subprocess = session.context.subprocess
        val root = session.layout.root
        val view = subprocess.run(listOf("gh", "pr", "view", pr.toString(), "--json", "headRefOid,comments"), workingDirectory = root)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Reading pull request #$pr")
        val json = ControlJson.lenient.parseToJsonElement(view.stdout).jsonObject
        val head = json.getValue("headRefOid").jsonPrimitive.content
        val bodies = json["comments"]?.jsonArray.orEmpty().map { it.jsonObject["body"]?.jsonPrimitive?.content.orEmpty() }
        val lines = parseVerifiedLines(bodies)
        val last = lines.lastOrNull() ?: throw ControlException(
            ErrorCode.ASSERTION_FAILED,
            "Pull request #$pr has no `Verified <sha> on <target>: ... run <run-id>` comment.",
            "Post one for the head's product code (verify-posato, Evidence).",
        )
        listOf(last.commit, head).forEach { commit ->
            if (!subprocess.run(listOf("git", "cat-file", "-e", "$commit^{commit}"), workingDirectory = root).succeeded) {
                subprocess.run(listOf("git", "fetch", "-q", "origin", "pull/$pr/head"), workingDirectory = root)
            }
        }
        val changed = subprocess.run(listOf("git", "diff", "--name-only", last.commit, head), workingDirectory = root)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Comparing ${last.commit} with the head $head")
            .stdout.lines().filter { it.isNotBlank() }
        val product = productChanges(changed)
        val cited = lines.flatMap { it.runIds }.distinct()
        val missing = cited.filterNot { session.layout.runsDirectory.resolve(it).exists() }
        val report = buildJsonObject {
            put("pr", pr)
            put("head", head)
            put("verifiedCommit", last.commit)
            putJsonArray("productChangesSinceVerified") { product.forEach { add(it) } }
            putJsonArray("citedRuns") { cited.forEach { add(it) } }
            putJsonArray("missingRuns") { missing.forEach { add(it) } }
        }
        val problems = listOfNotNull(
            "product code changed after ${last.commit}: ${product.joinToString()}".takeIf { product.isNotEmpty() },
            "cited runs missing under build/verification/runs: ${missing.joinToString()}".takeIf { missing.isNotEmpty() },
        )
        if (problems.isNotEmpty()) {
            throw ControlException(
                ErrorCode.ASSERTION_FAILED,
                "The evidence of #$pr does not hold: ${problems.joinToString("; ")}.",
                "Verify the head's product code again and post its Verified line, citing runs from this worktree.",
                result = report,
            )
        }
        return report
    }
}
