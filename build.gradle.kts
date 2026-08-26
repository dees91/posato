import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.metro) apply false
}

check(JavaVersion.current() == JavaVersion.VERSION_21) {
    "Posato requires JDK 21; the current Gradle runtime is ${JavaVersion.current()}."
}

val composeRulesDetektDependency = libs.compose.rules.detekt
val detektToolVersion = libs.versions.detekt.get()
val ktlintToolVersion = libs.versions.ktlint.asProvider()

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    val generatedSourceDirectory =
        layout.buildDirectory
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

tasks.register("quality") {
    group = "verification"
    description = "Runs Posato's formatting, analysis, test, compilation, packaging, and report checks."
    dependsOn(
        "ktlintCheck",
        ":desktopApp:createDistributable",
        ":desktopApp:detekt",
        ":desktopApp:ktlintCheck",
        ":desktopApp:test",
        ":shared:compileKotlinIosArm64",
        ":shared:compileKotlinIosSimulatorArm64",
        ":shared:detekt",
        ":shared:jvmTest",
        ":shared:ktlintCheck",
    )
}
