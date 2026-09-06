import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.multiplatform)
}

val windowChromeResources = layout.buildDirectory.dir("generated/window-chrome")
val windowChromeLibrary = windowChromeResources.map { it.file("macos-arm64/native/libPosatoPrototypeWindow.dylib") }
val prototypeJavaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
val compileWindowChrome = tasks.register<Exec>("compileWindowChrome") {
    val source = layout.projectDirectory.file("src/macosMain/objc/PrototypeWindowChrome.m")
    val javaInstallation = prototypeJavaLauncher.get().metadata.installationPath.asFile
    val compiledLibrary = windowChromeLibrary.get().asFile
    inputs.file(source)
    outputs.file(compiledLibrary)
    doFirst { compiledLibrary.parentFile.mkdirs() }
    commandLine(
        "xcrun",
        "clang",
        "-dynamiclib",
        "-fobjc-arc",
        "-Wall",
        "-Werror",
        "-target",
        "arm64-apple-macos15.0",
        "-framework",
        "AppKit",
        "-framework",
        "QuartzCore",
        "-I$javaInstallation/include",
        "-I$javaInstallation/include/darwin",
        source.asFile.absolutePath,
        "-o",
        compiledLibrary.absolutePath,
    )
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
            appResourcesRootDir.set(compileWindowChrome.map { windowChromeResources.get() })
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

tasks.withType<AbstractJPackageTask>().configureEach {
    inputs.file(compileWindowChrome.map { windowChromeLibrary.get() })
        .withPropertyName("prototypeWindowChrome")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register("verifyPrototype") {
    group = "verification"
    description = "Checks the mock prototype model, static analysis, and shared Simulator compilation."
    dependsOn("ktlintCheck", "detekt", "jvmTest", "compileKotlinIosSimulatorArm64")
}

tasks.register("verifyPrototypeHosts") {
    group = "verification"
    description = "Builds both prototype hosts and the device framework before native review."
    dependsOn("verifyPrototype", "linkDebugFrameworkIosArm64", "verifyIosPrototype", "createDistributable")
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
