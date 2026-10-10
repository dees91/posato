package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

/** A line that starts with the verification line, not one that quotes or mentions it. */
private val VERIFIED = Regex("""^Verified\s+([0-9a-f]{7,40})\b""")

/** Every `run <id>`, also a label passed as `--run-id`; trailing punctuation is not part of it. */
private val RUN_ID = Regex("""\brun\s+([A-Za-z0-9][A-Za-z0-9._-]*[A-Za-z0-9])""")

/** One pull request comment and the login that posted it. */
data class PrComment(
    val author: String,
    val body: String,
)

/** The verification lines among a pull request's comments, in their order. */
internal fun parseVerifiedLines(
    comments: List<PrComment>,
    trustedAuthors: Set<String>,
): List<VerifiedLine> = comments.filter { it.author in trustedAuthors }.map { it.body }.flatMap { comment ->
    comment.lines().map { it.trim() }.mapNotNull { line ->
        VERIFIED.find(line)?.let { match -> VerifiedLine(match.groupValues[1], RUN_ID.findAll(line).map { it.groupValues[1] }.toList()) }
    }
}

/**
 * The paths whose change after the verified commit invalidates the evidence: the application sources and their
 * builds, and the verification driver when the pull request changes it. Documentation, wiki, and skills may change
 * after the run.
 */
internal fun productPaths(
    paths: Collection<String>,
    includeTooling: Boolean,
): List<String> = paths.filter { path ->
    PRODUCT_PREFIXES.any { path.startsWith(it) } || path in PRODUCT_FILES || (includeTooling && path.startsWith(TOOLING_PREFIX))
}.sorted()

private const val TOOLING_PREFIX = "tools/posato-control/"

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
        val git = Git(session)
        val view = git.json(listOf("gh", "pr", "view", pr.toString(), "--json", "headRefOid,baseRefName,author,comments"))
        val head = view.getValue("headRefOid").jsonPrimitive.content
        val base = view.getValue("baseRefName").jsonPrimitive.content
        val author = view["author"]?.jsonObject?.get("login")?.jsonPrimitive?.content.orEmpty()
        val owner = git.json(listOf("gh", "repo", "view", "--json", "owner")).getValue("owner").jsonObject.getValue("login").jsonPrimitive.content
        val comments = view["comments"]?.jsonArray.orEmpty().map { comment ->
            val body = comment.jsonObject
            PrComment(body["author"]?.jsonObject?.get("login")?.jsonPrimitive?.content.orEmpty(), body["body"]?.jsonPrimitive?.content.orEmpty())
        }
        val lines = parseVerifiedLines(comments, setOf(author, owner))
        val last = lines.lastOrNull() ?: throw ControlException(
            ErrorCode.ASSERTION_FAILED,
            "Pull request #$pr has no `Verified <sha> on <target>: ... run <run-id>` line at the start of a comment by its author or $owner.",
            "Post one for the head's product code (verify-posato, Evidence).",
        )
        git.fetch(base, pr)
        // Each side against its own merge base, so a rebase onto a newer main does not count as a change.
        val headBase = git.mergeBase("origin/$base", head)
        val verifiedBase = git.mergeBase("origin/$base", last.commit)
        val headPaths = git.changed(headBase, head)
        val scope = productPaths(headPaths + git.changed(verifiedBase, last.commit), headPaths.any { it.startsWith(TOOLING_PREFIX) })
        val product = scope.filter { path -> git.patchId(verifiedBase, last.commit, path) != git.patchId(headBase, head, path) }
        val cited = lines.flatMap { it.runIds }.distinct()
        val missing = cited.filterNot { session.layout.runsDirectory.resolve(it).exists() }
        val report = buildJsonObject {
            put("pr", pr)
            put("head", head)
            put("verifiedCommit", last.commit)
            put("author", author)
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

/** The few Git and GitHub reads `pr-evidence` needs, each failing as a command failure with what it was doing. */
private class Git(
    private val session: Session,
) {
    private val root = session.layout.root

    fun json(command: List<String>): JsonObject = ControlJson.lenient.parseToJsonElement(
        session.context.subprocess.run(command, workingDirectory = root)
            .requireSuccess(ErrorCode.COMMAND_FAILED, command.joinToString(" ")).stdout,
    ).jsonObject

    fun fetch(
        base: String,
        pr: Int,
    ) {
        session.context.subprocess.run(listOf("git", "fetch", "-q", "origin", base, "pull/$pr/head"), workingDirectory = root)
    }

    fun mergeBase(
        base: String,
        commit: String,
    ): String = run(listOf("git", "merge-base", base, commit), "Finding the merge base of $commit").trim()

    fun changed(
        from: String,
        to: String,
    ): List<String> = run(listOf("git", "diff", "--name-only", from, to), "Listing the changes of $to").lines().filter { it.isNotBlank() }

    /** The stable patch id of one path's change, empty when the side does not change it. */
    fun patchId(
        from: String,
        to: String,
        path: String,
    ): String {
        val diff = run(listOf("git", "diff", from, to, "--", path), "Reading the change of $path")
        if (diff.isBlank()) return ""
        return session.context.subprocess.run(listOf("git", "patch-id", "--stable"), workingDirectory = root, stdin = diff)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Hashing the change of $path").stdout.substringBefore(' ')
    }

    private fun run(
        command: List<String>,
        what: String,
    ): String = session.context.subprocess.run(command, workingDirectory = root).requireSuccess(ErrorCode.COMMAND_FAILED, what).stdout
}
