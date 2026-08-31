package app.posato.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyApprovedQualityExceptions : DefaultTask() {
    private data class Inspection(
        val unapprovedLines: List<Int>,
        val matchedApprovals: List<String>,
    )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSources: ConfigurableFileCollection

    @get:Input
    abstract val approvedExceptions: ListProperty<String>

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val rootDirectory = repositoryDirectory.get().asFile
        val exceptionName = "Supp" + "ress"
        val exceptionToken = Regex("""\b$exceptionName\b""")
        verifyDetectionContract(exceptionToken, exceptionName)

        val remainingApprovals = approvedExceptions.get().toMutableList()
        val unapprovedExceptions = kotlinSources.files
            .sortedBy { it.relativeTo(rootDirectory).invariantSeparatorsPath }
            .flatMap { source ->
                val relativePath = source.relativeTo(rootDirectory).invariantSeparatorsPath
                val sourceApprovalPrefix = "$relativePath:"
                val sourceApprovals = remainingApprovals
                    .filter { it.startsWith(sourceApprovalPrefix) }
                    .map { it.removePrefix(sourceApprovalPrefix) }
                val inspection = inspect(source.readText(), sourceApprovals, exceptionToken)
                inspection.matchedApprovals.forEach { matchedApproval ->
                    remainingApprovals.remove(sourceApprovalPrefix + matchedApproval)
                }
                inspection.unapprovedLines.map { lineNumber -> "$relativePath:$lineNumber" }
            }

        if (unapprovedExceptions.isNotEmpty() || remainingApprovals.isNotEmpty()) {
            val unapprovedMessage = unapprovedExceptions.takeIf { it.isNotEmpty() }?.joinToString(
                prefix = "Unapproved Kotlin quality exceptions found:\n",
                separator = "\n",
            ) { location -> "- $location" }
            val staleMessage = remainingApprovals.takeIf { it.isNotEmpty() }?.joinToString(
                prefix = "Stale Kotlin quality-exception approvals found:\n",
                separator = "\n",
            ) { approval -> "- $approval" }
            throw GradleException(
                listOfNotNull(unapprovedMessage, staleMessage).joinToString(separator = "\n") + "\n" +
                    "Fix the underlying issue. A suppression may be allowlisted only after explicit maintainer approval.",
            )
        }
    }

    private fun verifyDetectionContract(
        exceptionToken: Regex,
        exceptionName: String,
    ) {
        val marker = "@"
        val fixtures = listOf(
            marker + exceptionName + "(\"Rule\")",
            marker + "file:kotlin." + exceptionName + "(\n/* reason (temporary) */ \"Rule\",\n)",
            marker + "get:" + exceptionName + "(\"Rule\")",
            "import kotlin.$exceptionName as Ignore",
            "typealias Hidden = kotlin.$exceptionName",
            "// " + marker + exceptionName + " examples are forbidden in Kotlin source",
            "val example = \"" + marker + exceptionName + "(\\\"Rule\\\")\"",
        )
        val approvedAnnotation = marker + exceptionName + "(\"Approved\")"
        val approvedFixture = approvedAnnotation + "\nfun approvedTarget() = Unit"
        val approvedInspection = inspect(approvedFixture, listOf(approvedFixture), exceptionToken)
        val movedInspection = inspect(
            approvedAnnotation + "\nfun broaderTarget() = Unit",
            listOf(approvedFixture),
            exceptionToken,
        )
        val duplicateInspection = inspect(
            approvedFixture + "\n" + approvedFixture,
            listOf(approvedFixture),
            exceptionToken,
        )
        val disguisedApprovals = listOf(
            "// $approvedFixture",
            "/* $approvedFixture */",
            "/* outer /* inner */ $approvedFixture outer */",
            "val example = \"$approvedFixture\"",
            "val example = \"\"\"$approvedFixture\"\"\"",
        )
        val staleInspection = inspect("val clean = Unit", listOf(approvedFixture), exceptionToken)
        val contractFailed = fixtures.any { fixture ->
            inspect(fixture, emptyList(), exceptionToken).unapprovedLines != listOf(1)
        } || approvedInspection.unapprovedLines.isNotEmpty() ||
            approvedInspection.matchedApprovals != listOf(approvedFixture) ||
            movedInspection.matchedApprovals.isNotEmpty() ||
            duplicateInspection.matchedApprovals.isNotEmpty() ||
            disguisedApprovals.any { fixture ->
                inspect(fixture, listOf(approvedFixture), exceptionToken).matchedApprovals.isNotEmpty()
            } ||
            staleInspection.matchedApprovals.isNotEmpty()
        if (contractFailed) {
            throw GradleException("The Kotlin quality-exception detector failed its regression contract.")
        }
    }

    private fun inspect(
        content: String,
        approvals: List<String>,
        exceptionToken: Regex,
    ): Inspection {
        var contentWithoutApprovals = content
        val matchedApprovals = mutableListOf<String>()
        approvals.forEach { approval ->
            val approvalIndexes = contentWithoutApprovals.windowedSequence(approval.length, 1)
                .mapIndexedNotNull { index, candidate -> index.takeIf { candidate == approval } }
                .toList()
            val tokenMatches = exceptionToken.findAll(approval).toList()
            val approvalIndex = approvalIndexes.singleOrNull()
            val tokenMatch = tokenMatches.singleOrNull()
            if (
                approvalIndex != null &&
                tokenMatch != null &&
                contentWithoutApprovals.isKotlinCodeAt(approvalIndex + tokenMatch.range.first)
            ) {
                contentWithoutApprovals = contentWithoutApprovals.replaceRange(
                    approvalIndex,
                    approvalIndex + approval.length,
                    approval.map { character -> if (character == '\n') '\n' else ' ' }.joinToString(""),
                )
                matchedApprovals += approval
            }
        }
        val unapprovedLines = exceptionToken.findAll(contentWithoutApprovals).map { match ->
            contentWithoutApprovals.countNewlinesBefore(match.range.first) + 1
        }.toList()
        return Inspection(unapprovedLines = unapprovedLines, matchedApprovals = matchedApprovals)
    }

    private fun String.countNewlinesBefore(index: Int): Int = take(index).count { it == '\n' }

    private fun String.isKotlinCodeAt(index: Int): Boolean {
        var position = 0
        var blockCommentDepth = 0
        var quote: Char? = null
        var rawString = false
        var escaped = false
        while (position < index) {
            val character = this[position]
            val next = getOrNull(position + 1)
            when {
                blockCommentDepth > 0 && character == '/' && next == '*' -> {
                    blockCommentDepth++
                    position++
                }

                blockCommentDepth > 0 && character == '*' && next == '/' -> {
                    blockCommentDepth--
                    position++
                }

                blockCommentDepth > 0 -> {
                    Unit
                }

                rawString && startsWith("\"\"\"", position) -> {
                    rawString = false
                    position += 2
                }

                rawString -> {
                    Unit
                }

                quote != null && escaped -> {
                    escaped = false
                }

                quote != null && character == '\\' -> {
                    escaped = true
                }

                quote != null && character == quote -> {
                    quote = null
                }

                quote != null -> {
                    Unit
                }

                character == '/' && next == '/' -> {
                    val newline = indexOf('\n', position + 2)
                    if (newline < 0 || index < newline) return false
                    position = newline
                }

                character == '/' && next == '*' -> {
                    blockCommentDepth = 1
                    position++
                }

                startsWith("\"\"\"", position) -> {
                    rawString = true
                    position += 2
                }

                character == '\"' || character == '\'' -> {
                    quote = character
                }
            }
            position++
        }
        return blockCommentDepth == 0 && !rawString && quote == null
    }
}
