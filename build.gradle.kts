import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
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
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

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

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.sqldelight) apply false
}

check(JavaVersion.current() == JavaVersion.VERSION_21) {
    "Posato requires JDK 21; the current Gradle runtime is ${JavaVersion.current()}."
}

val composeRulesDetektDependency = libs.compose.rules.detekt
val detektToolVersion = libs.versions.detekt.get()
val ktlintToolVersion = libs.versions.ktlint.asProvider()

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies.add("ktlintRuleset", rootProject.project(":quality-rules"))

    val generatedSourceDirectory = layout.buildDirectory
        .dir("generated")
        .get()
        .asFile

    configure<KtlintExtension> {
        version.set(ktlintToolVersion)
        ignoreFailures.set(false)
        outputToConsole.set(true)
        filter {
            exclude { fileTreeElement ->
                fileTreeElement.file.toPath().startsWith(generatedSourceDirectory.toPath())
            }
        }
        reporters {
            reporter(ReporterType.PLAIN)
            reporter(ReporterType.CHECKSTYLE)
        }
    }
}

subprojects {
    apply(plugin = "dev.detekt")

    configure<DetektExtension> {
        toolVersion = detektToolVersion
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        ignoreFailures = false
        failOnSeverity = FailOnSeverity.Info
        source.setFrom(
            fileTree("src") {
                include("**/*.kt", "**/*.kts")
            },
        )
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        basePath.set(rootProject.projectDir)
    }

    dependencies.add("detektPlugins", composeRulesDetektDependency)

    tasks.withType<Detekt>().configureEach {
        jvmTarget.set("17")
        reports {
            checkstyle.required.set(true)
            html.required.set(true)
            markdown.required.set(true)
            sarif.required.set(true)
        }
    }
}

val verifyApprovedQualityExceptions by tasks.registering(VerifyApprovedQualityExceptions::class) {
    group = "verification"
    description = "Rejects Kotlin suppressions that do not have explicit maintainer approval."

    kotlinSources.from(
        fileTree(rootDir) {
            include("**/*.kt", "**/*.kts")
            exclude("**/build/**", "**/.gradle/**", ".research/**")
        },
    )
    repositoryDirectory.set(layout.projectDirectory)
    val annotationMarker = "@"
    val exceptionName = "Supp" + "ress"
    approvedExceptions.set(
        listOf(
            "desktopApp/src/main/kotlin/app/posato/desktop/macos/MacOsHelperClient.kt:" +
                annotationMarker + "file:$exceptionName(\"MagicNumber\", \"TooManyFunctions\")",
            "desktopApp/src/main/kotlin/app/posato/desktop/macos/MacOsHelperClient.kt:" +
                annotationMarker + "$exceptionName(\"TooGenericExceptionCaught\")",
            "desktopApp/src/main/kotlin/app/posato/desktop/macos/MacOsHelperClient.kt:" +
                annotationMarker + "$exceptionName(\"ThrowsCount\")",
            "desktopApp/src/main/kotlin/app/posato/desktop/macos/MacOsHelperProtocol.kt:" +
                annotationMarker + "file:$exceptionName(\"MagicNumber\")",
            "shared/src/iosMain/kotlin/app/posato/MainViewController.kt:" +
                annotationMarker + "$exceptionName(\"FunctionNaming\", \"ktlint:standard:function-naming\")",
            "shared/src/iosMain/kotlin/app/posato/feature/targets/domain/ApplicationPolicyName.ios.kt:" +
                annotationMarker + "file:$exceptionName(\"CAST_NEVER_SUCCEEDS\")",
        ),
    )
}

tasks.register("quality") {
    group = "verification"
    description = "Runs Posato's formatting, analysis, test, compilation, packaging, and report checks."
    dependsOn(
        "ktlintCheck",
        ":desktopApp:createDistributable",
        ":desktopApp:verifyMacOsHelperPackaging",
        ":desktopApp:detekt",
        ":desktopApp:ktlintCheck",
        ":desktopApp:test",
        ":quality-rules:detekt",
        ":quality-rules:ktlintCheck",
        ":quality-rules:test",
        ":shared:compileKotlinIosArm64",
        ":shared:compileKotlinIosSimulatorArm64",
        ":shared:compileAndroidMain",
        ":shared:detekt",
        ":shared:iosSimulatorArm64Test",
        ":shared:jvmTest",
        ":shared:ktlintCheck",
        ":shared:verifySqlDelightMigration",
        ":macosHelper:check",
        verifyApprovedQualityExceptions,
    )
}
