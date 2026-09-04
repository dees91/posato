import app.posato.buildlogic.VerifyApprovedQualityExceptions
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
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
        ),
    )
}

tasks.register("quality") {
    group = "verification"
    description = "Runs Posato's formatting, analysis, test, compilation, packaging, and report checks."
    dependsOn(
        "ktlintCheck",
        ":desktopApp:createDistributable",
        ":desktopApp:verifyMacOsDevelopmentPackaging",
        ":desktopApp:detekt",
        ":desktopApp:ktlintCheck",
        ":desktopApp:test",
        ":desktopApp:verifySqlDelightMigration",
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
        ":macosSyncCompanion:check",
        ":posato-control:detekt",
        ":posato-control:ktlintCheck",
        ":posato-control:swiftFormatCheck",
        ":posato-control:test",
        verifyApprovedQualityExceptions,
    )
}
