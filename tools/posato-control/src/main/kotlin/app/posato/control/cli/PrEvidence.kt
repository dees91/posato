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

/** Every `run <word>`; trailing punctuation is not part of it. */
private val RUN_WORD = Regex("""\brun\s+([A-Za-z0-9._-]+)""")

/** One pull request comment and the login that posted it. */
data class PrComment(
    val author: String,
    val body: String,
)

/**
 * The verification lines among a pull request's comments, in their order, with the runs each cites: a word after
 * "run" that holds a digit, as generated ids do, or that names a run directory, as a `--run-id` label may. An ordinary
 * word after "run" is neither, so a line citing only a label whose run is missing cites nothing.
 */
internal fun parseVerifiedLines(
    comments: List<PrComment>,
    trustedAuthors: Set<String>,
    runExists: (String) -> Boolean,
): List<VerifiedLine> = comments.filter { it.author in trustedAuthors }.map { it.body }.flatMap { comment ->
    comment.lines().map { it.trim() }.mapNotNull { line ->
        VERIFIED.find(line)?.let { match ->
            val words = RUN_WORD.findAll(line).map { it.groupValues[1].trimEnd('.', '-', '_') }
            VerifiedLine(match.groupValues[1], words.filter { word -> word.any(Char::isDigit) || runExists(word) }.toList())
        }
    }
}

/**
 * The paths whose difference between the verified commit and the head invalidates the evidence: every path except
 * documentation (`docs/` and any `.md` file, also a README inside a product directory) and agent instructions
 * (`.agents/` and `.claude/`, the skills). It is the pathspec `. ':!docs/' ':!*.md' ':!.agents/' ':!.claude/'`, so new
 * application directories count without a list to keep current.
 */
internal fun productPaths(paths: Collection<String>): List<String> = paths.filterNot { path ->
    path.endsWith(".md") || NON_PRODUCT_PREFIXES.any { path.startsWith(it) }
}.sorted()

private val NON_PRODUCT_PREFIXES = listOf("docs/", ".agents/", ".claude/")

/**
 * The product paths among [differing], the paths whose content differs between the verified commit and the head. With
 * the same merge base on both sides each one is a change after the run. After a rebase a path counts only when
 * [ownChangeDiffers]: the pull request's own change of it, against each side's merge base, is not the same patch, so
 * what a newer base branch brought does not count.
 */
internal fun productChangesSinceVerified(
    differing: Collection<String>,
    rebased: Boolean,
    ownChangeDiffers: (String) -> Boolean,
): List<String> = productPaths(differing).filter { path -> !rebased || ownChangeDiffers(path) }

/**
 * Checks a pull request's verification evidence before it is marked ready or handed to `babysit-pr`: its last
 * `Verified <sha>` comment must name a commit whose product tree equals the head's ([productPaths]; after a rebase,
 * whose own product change equals the head's), and every run any `Verified` comment cites must exist under this
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
        val runs = session.layout.runsDirectory
        val lines = parseVerifiedLines(comments, setOf(author, owner)) { runs.resolve(it).exists() }
        val last = lines.lastOrNull() ?: throw ControlException(
            ErrorCode.ASSERTION_FAILED,
            "Pull request #$pr has no `Verified <sha> on <target>: ... run <run-id>` line at the start of a comment by its author or $owner.",
            "Post one for the head's product code (verify-posato, Evidence).",
        )
        git.fetch(base, pr)
        // Each side against its own merge base, so a rebase onto a newer main does not count as a change.
        val headBase = git.mergeBase("origin/$base", head)
        val verifiedBase = git.mergeBase("origin/$base", last.commit)
        val product = productChangesSinceVerified(git.changed(last.commit, head), rebased = headBase != verifiedBase) { path ->
            git.patchId(verifiedBase, last.commit, path) != git.patchId(headBase, head, path)
        }
        val cited = lines.flatMap { it.runIds }.distinct()
        val missing = cited.filterNot { runs.resolve(it).exists() }
        val uncited = lines.filter { it.runIds.isEmpty() }.map { it.commit }
        val report = buildJsonObject {
            put("pr", pr)
            put("head", head)
            put("verifiedCommit", last.commit)
            put("author", author)
            putJsonArray("productChangesSinceVerified") { product.forEach { add(it) } }
            putJsonArray("citedRuns") { cited.forEach { add(it) } }
            putJsonArray("missingRuns") { missing.forEach { add(it) } }
            putJsonArray("linesWithoutRun") { uncited.forEach { add(it) } }
        }
        val problems = listOfNotNull(
            "the product tree of ${last.commit.take(SHORT_SHA)} differs from the head ${head.take(SHORT_SHA)} in ${product.joinToString()}"
                .takeIf { product.isNotEmpty() },
            "cited runs missing under build/verification/runs: ${missing.joinToString()}".takeIf { missing.isNotEmpty() },
            "Verified lines citing no existing run: ${uncited.joinToString()}".takeIf { uncited.isNotEmpty() },
        )
        if (problems.isNotEmpty()) {
            throw ControlException(
                ErrorCode.ASSERTION_FAILED,
                "The evidence of #$pr does not hold: ${problems.joinToString("; ")}.",
                "Verify the head again and post `Verified ${head.take(SHORT_SHA)} on <target>: ... run <run-id>`, citing runs from " +
                    "this worktree; only docs/, .md files, .agents/, and .claude/ may change after a run.",
                result = report,
            )
        }
        return report
    }
}

private const val SHORT_SHA = 8

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
    ): List<String> = run(listOf("git", "diff", "--no-renames", "--name-only", from, to), "Listing the changes of $to").lines().filter {
        it.isNotBlank()
    }

    /** The stable patch id of one path's change, empty when the side does not change it. */
    fun patchId(
        from: String,
        to: String,
        path: String,
    ): String {
        val diff = run(listOf("git", "diff", "--no-renames", from, to, "--", path), "Reading the change of $path")
        if (diff.isBlank()) return ""
        return session.context.subprocess.run(listOf("git", "patch-id", "--verbatim"), workingDirectory = root, stdin = diff)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Hashing the change of $path").stdout.substringBefore(' ')
    }

    private fun run(
        command: List<String>,
        what: String,
    ): String = session.context.subprocess.run(command, workingDirectory = root).requireSuccess(ErrorCode.COMMAND_FAILED, what).stdout
}
