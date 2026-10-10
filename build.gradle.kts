import app.posato.buildlogic.IosSwiftTestTrigger
import app.posato.buildlogic.VerifyApprovedQualityExceptions
import app.posato.buildlogic.VerifyEnglishText
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
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

configure<KtlintExtension> {
    kotlinScriptAdditionalPaths {
        include(
            fileTree("buildSrc") {
                include("**/*.kt", "**/*.kts")
                exclude("**/build/**")
            },
        )
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

val verifyEnglishText by tasks.registering(VerifyEnglishText::class) {
    group = "verification"
    description = "Rejects Polish letters in the repository's documentation, skills, site, and sources."
    textFiles.from(
        fileTree(rootDir) {
            // Named, not *.md: THIRD_PARTY_NOTICES.md reproduces copyright notices as their holders wrote them.
            include("README.md", "DESIGN.md", "AGENTS.md", "CLAUDE.md", "CONTRIBUTING.md", "PRIVACY.md", "SECURITY.md")
            listOf(
                "docs",
                ".agents",
                "website/src",
                "video",
                "shared/src",
                "desktopApp/src",
                "iosApp",
                "macosHelper",
                "macosSyncCompanion",
                "tools",
                "buildSrc/src",
                "quality-rules",
                "prototypes",
            ).forEach { directory -> include("$directory/**") }
            exclude("**/build/**", "**/.gradle/**", "**/node_modules/**", "**/DerivedData*/**", "**/.build/**", "**/out/**")
        },
    )
    // A future Polish localization lives in its own directories, such as values-pl or pl.lproj; list them here.
    excludedDirectories.set(emptyList<String>())
    exceptions.set(emptyMap<String, String>())
    repositoryDirectory.set(layout.projectDirectory)
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
                annotationMarker + "file:$exceptionName(\"MagicNumber\", \"TooManyFunctions\")" +
                "\n\npackage app.posato.desktop.macos",
            "desktopApp/src/main/kotlin/app/posato/desktop/macos/MacOsHelperClient.kt:" +
                annotationMarker + "$exceptionName(\"TooGenericExceptionCaught\")" +
                "\n    private fun ensureStarted()",
            "desktopApp/src/main/kotlin/app/posato/desktop/macos/MacOsHelperClient.kt:" +
                annotationMarker + "$exceptionName(\"ThrowsCount\")" +
                "\n    private fun readWithDeadline(",
            "desktopApp/src/main/kotlin/app/posato/desktop/macos/MacOsHelperProtocol.kt:" +
                annotationMarker + "file:$exceptionName(\"MagicNumber\")" +
                "\n\npackage app.posato.desktop.macos",
            "shared/src/iosMain/kotlin/app/posato/feature/targets/domain/ApplicationPolicyName.ios.kt:" +
                annotationMarker + "file:$exceptionName(\"CAST_NEVER_SUCCEEDS\")" +
                "\n\npackage app.posato.feature.targets.domain",
            "shared/src/commonMain/kotlin/app/posato/feature/session/ui/SessionTransitionOwner.kt:" +
                annotationMarker + exceptionName + "(\"TooManyFunctions\")" +
                "\ninternal class SessionTransitionOwner(",
        ),
    )
}

val iosSwiftTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs native Swift XCTest suites on an isolated, credential-free iOS Simulator."
    dependsOn(":shared:linkDebugFrameworkIosSimulatorArm64", ":shared:iosSimulatorArm64AggregateResources")
    workingDir(layout.projectDirectory)
    commandLine("bash", "tools/quality/ios-swift-test.sh")
}

// The suites cost about 145 s per run, nearly all of it Simulator setup and the test host's launch, and they guard
// code that few changes touch, so quality runs them only for those changes; `./gradlew quality iosSwiftTest` always
// does, as the release procedure requires (`user-confirmed` 2026-10-08).
val iosSwiftTestTrigger = providers.of(IosSwiftTestTrigger::class) {
    parameters.repositoryDirectory.set(layout.projectDirectory)
    parameters.baseRef.set("origin/main")
    parameters.guardedPaths.set(
        listOf(
            "iosApp/",
            "shared/src/iosMain/",
            "shared/build.gradle.kts",
            "shared/src/commonMain/kotlin/app/posato/feature/enforcement/ExpiryDisplacement.kt",
            "shared/src/commonMain/kotlin/app/posato/feature/enforcement/PauseComposition.kt",
            "gradle.properties",
            "gradle/libs.versions.toml",
            "tools/quality/ios-swift-test.sh",
            "buildSrc/src/main/kotlin/app/posato/buildlogic/IosSwiftTestTrigger.kt",
        ),
    )
}

val iosHostBuildCheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Compiles unsigned Debug iOS device and Release Simulator hosts."
    dependsOn(":shared:linkDebugFrameworkIosArm64", ":shared:linkReleaseFrameworkIosSimulatorArm64")
    mustRunAfter(iosSwiftTest)
    workingDir(layout.projectDirectory)
    commandLine("bash", "tools/quality/ios-host-build-check.sh")
}

tasks.register("qualityLint") {
    group = "verification"
    description = "Runs only Posato's formatting and static analysis, in about a minute, before a commit; quality still gates the push."
    dependsOn(
        "ktlintCheck",
        ":desktopApp:detekt",
        ":desktopApp:ktlintCheck",
        ":linuxHelper:detekt",
        ":linuxHelper:ktlintCheck",
        ":quality-rules:detekt",
        ":quality-rules:ktlintCheck",
        ":shared:detekt",
        ":shared:ktlintCheck",
        ":macosHelper:detekt",
        ":macosHelper:ktlintCheck",
        ":macosHelper:swiftFormatCheck",
        ":macosHelper:swiftLintCheck",
        ":macosSyncCompanion:detekt",
        ":macosSyncCompanion:ktlintCheck",
        ":macosSyncCompanion:swiftFormatCheck",
        ":macosSyncCompanion:swiftLintCheck",
        ":prototypeApp:detekt",
        ":prototypeApp:ktlintCheck",
        ":prototypeDesignSystem:detekt",
        ":prototypeDesignSystem:ktlintCheck",
        ":posato-control:detekt",
        ":posato-control:ktlintCheck",
        ":posato-provisioning:detekt",
        ":posato-provisioning:ktlintCheck",
        ":posato-control:swiftFormatCheck",
        verifyApprovedQualityExceptions,
        verifyEnglishText,
    )
}

tasks.register("quality") {
    group = "verification"
    description = "Runs Posato's formatting, analysis, test, compilation, packaging, and report checks."
    val iosSwiftTestReason = gradle.startParameter.taskNames
        .firstOrNull { it.removePrefix(":") == iosSwiftTest.name }
        ?.let { "requested by name" }
        ?: iosSwiftTestTrigger.orNull
    if (iosSwiftTestReason != null) dependsOn(iosSwiftTest)
    doLast {
        println(
            iosSwiftTestReason?.let { "Swift XCTest ran: $it." }
                ?: "Swift XCTest not run: no file the suites guard differs from origin/main; " +
                "./gradlew quality iosSwiftTest runs them.",
        )
    }
    dependsOn(
        "ktlintCheck",
        iosHostBuildCheck,
        ":desktopApp:createDistributable",
        ":desktopApp:verifyMacOsDevelopmentPackaging",
        ":desktopApp:checkIntelNativeLeaves",
        ":desktopApp:detekt",
        ":desktopApp:ktlintCheck",
        ":desktopApp:test",
        ":desktopApp:verifySqlDelightMigration",
        ":linuxHelper:detekt",
        ":linuxHelper:ktlintCheck",
        ":linuxHelper:test",
        ":quality-rules:detekt",
        ":quality-rules:ktlintCheck",
        ":quality-rules:test",
        ":prototypeDesignSystem:verifyDesignSystem",
        ":prototypeApp:verifyPrototype",
        ":shared:compileKotlinIosArm64",
        ":shared:compileKotlinIosSimulatorArm64",
        ":shared:compileAndroidMain",
        ":shared:detekt",
        ":shared:iosSimulatorArm64Test",
        ":shared:jvmTest",
        ":shared:ktlintCheck",
        ":shared:verifySqlDelightMigration",
        ":macosHelper:check",
        ":macosSyncCompanion:check",
        ":posato-control:detekt",
        ":posato-control:ktlintCheck",
        ":posato-control:swiftFormatCheck",
        ":posato-control:test",
        ":posato-provisioning:detekt",
        ":posato-provisioning:ktlintCheck",
        ":posato-provisioning:test",
        verifyApprovedQualityExceptions,
        verifyEnglishText,
    )
}
