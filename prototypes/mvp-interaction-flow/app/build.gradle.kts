import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PosatoPrototype"
            isStatic = true
        }
    }
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":prototypeDesignSystem"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.kuri)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(libs.compose.desktop.macos.arm64)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.posato.prototype.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Posato Prototype"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "app.posato.prototype.macos"
                minimumSystemVersion = "15.0"
            }
        }
    }
}

tasks.register("verifyPrototype") {
    group = "verification"
    description = "Checks the mock prototype model and compiles both native hosts."
    dependsOn("ktlintCheck", "detekt", "jvmTest", "linkDebugFrameworkIosArm64", "verifyIosPrototype", "createDistributable")
}

tasks.register<Exec>("verifyIosPrototype") {
    group = "verification"
    description = "Builds the prototype iPhone host for Simulator without signing or product services."
    dependsOn("linkDebugFrameworkIosSimulatorArm64")
    commandLine(
        "xcodebuild",
        "-project",
        "../iosApp/PosatoPrototype.xcodeproj",
        "-scheme",
        "PosatoPrototype",
        "-configuration",
        "Debug",
        "-sdk",
        "iphonesimulator",
        "-destination",
        "generic/platform=iOS Simulator",
        "-derivedDataPath",
        rootProject.layout.buildDirectory.dir("verification/prototype-ios").get().asFile.absolutePath,
        "CODE_SIGNING_ALLOWED=NO",
        "OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES",
        "FRAMEWORK_SEARCH_PATHS=${layout.buildDirectory.dir("bin/iosSimulatorArm64/debugFramework").get().asFile.absolutePath}",
        "build",
    )
}
