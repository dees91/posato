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
        val approvedFixture = marker + exceptionName + "(\"Approved\")"
        val approvedInspection = inspect(approvedFixture, listOf(approvedFixture), exceptionToken)
        val staleInspection = inspect("val clean = Unit", listOf(approvedFixture), exceptionToken)
        val contractFailed = fixtures.any { fixture ->
            inspect(fixture, emptyList(), exceptionToken).unapprovedLines != listOf(1)
        } || approvedInspection.unapprovedLines.isNotEmpty() ||
            approvedInspection.matchedApprovals != listOf(approvedFixture) ||
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
            val approvalIndex = contentWithoutApprovals.indexOf(approval)
            if (approvalIndex >= 0) {
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
}
