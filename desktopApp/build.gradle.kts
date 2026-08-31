import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class VerifyMacOsHelperStructure : DefaultTask() {
    @get:InputFiles
    abstract val requiredFiles: ConfigurableFileCollection

    @get:InputFile
    abstract val helperInfoPlist: RegularFileProperty

    @get:InputFile
    abstract val daemonPlist: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun verify() {
        requiredFiles.files.forEach { requiredFile ->
            check(requiredFile.isFile) { "Required packaged file is missing: ${requiredFile.name}" }
        }
        val helperInfo = helperInfoPlist.get().asFile
        val daemon = daemonPlist.get().asFile
        check(plistValue(helperInfo, ":CFBundleIdentifier") == "app.posato.macos.helper")
        check(plistValue(helperInfo, ":CFBundleExecutable") == "PosatoMacOSHelper")
        check(plistValue(helperInfo, ":CFBundlePackageType") == "APPL")
        check(plistValue(helperInfo, ":LSMinimumSystemVersion") == "15.0")
        check(plistValue(helperInfo, ":LSUIElement") == "true")
        check(plistValue(daemon, ":Label") == "app.posato.macos.proxy-settings")
        check(plistValue(daemon, ":BundleProgram") == "Contents/Resources/PosatoProxySettingsDaemon")
        check(plistValue(daemon, ":MachServices:app.posato.macos.proxy-settings") == "true")
        check(plistValue(daemon, ":KeepAlive:SuccessfulExit") == "false")
        check(plistValue(daemon, ":ProcessType") == "Background")
        check(plistValue(daemon, ":Umask") == "63")
    }

    private fun plistValue(
        file: File,
        keyPath: String,
    ): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("/usr/libexec/PlistBuddy", "-c", "Print $keyPath", file.absolutePath)
            standardOutput = output
        }
        return output.toString(Charsets.UTF_8).trim()
    }
}

val macOsHelperBundle = project(":macosHelper").layout.buildDirectory
    .dir("bundle/PosatoMacOSHelper.app")
val macOsDistributable = layout.buildDirectory.dir(
    "compose/binaries/main/app/Posato.app",
)
val macOsSigningIdentity = providers.gradleProperty("posatoMacOsSigningIdentity")
    .orElse("-")
    .get()
val macOsDaemonExecutable = macOsDistributable.get().file(
    "Contents/Helpers/PosatoMacOSHelper.app/Contents/Resources/PosatoProxySettingsDaemon",
).asFile.absolutePath
val macOsHelperApplication = macOsDistributable.get().dir(
    "Contents/Helpers/PosatoMacOSHelper.app",
).asFile.absolutePath
val macOsApplication = macOsDistributable.get().asFile.absolutePath

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.compose.desktop.macos.arm64)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.sqlite.driver)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

sqldelight {
    databases {
        create("MacOsApplicationMappingsDatabase") {
            packageName.set("app.posato.desktop.mappings.database")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.posato.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Posato"
            packageVersion = "1.0.0"
            modules("java.sql")

            macOS {
                bundleID = "app.posato.macos"
                minimumSystemVersion = "15.0"
            }
        }
    }
}

val embedMacOsHelper by tasks.registering(Sync::class) {
    group = "build"
    description = "Embeds the native macOS enforcement helper in the distributable."
    dependsOn(":macosHelper:assembleHelperBundle", "createDistributable")

    from(macOsHelperBundle)
    into(
        macOsDistributable.map { app ->
            app.dir("Contents/Helpers/PosatoMacOSHelper.app")
        },
    )
}

val signMacOsDaemon by tasks.registering(Exec::class) {
    group = "build"
    description = "Signs the embedded proxy-settings daemon."
    dependsOn(embedMacOsHelper)
    inputs.property("signingIdentity", macOsSigningIdentity)

    commandLine(
        "/usr/bin/codesign",
        "--force",
        "--options",
        "runtime",
        "--timestamp=none",
        "--identifier",
        "app.posato.macos.proxy-settings",
        "--sign",
        macOsSigningIdentity,
        macOsDaemonExecutable,
    )
}

val signMacOsHelper by tasks.registering(Exec::class) {
    group = "build"
    description = "Signs the embedded normal-user macOS helper."
    dependsOn(signMacOsDaemon)
    inputs.property("signingIdentity", macOsSigningIdentity)

    commandLine(
        "/usr/bin/codesign",
        "--force",
        "--options",
        "runtime",
        "--timestamp=none",
        "--identifier",
        "app.posato.macos.helper",
        "--sign",
        macOsSigningIdentity,
        macOsHelperApplication,
    )
}

val signMacOsHelperApp by tasks.registering(Exec::class) {
    group = "build"
    description = "Signs the final macOS application after helper embedding."
    dependsOn(signMacOsHelper)
    inputs.property("signingIdentity", macOsSigningIdentity)

    commandLine(
        "/usr/bin/codesign",
        "--force",
        "--options",
        "runtime",
        "--timestamp=none",
        "--sign",
        macOsSigningIdentity,
        macOsApplication,
    )
}

val verifyMacOsHelperStructure by tasks.registering(VerifyMacOsHelperStructure::class) {
    group = "verification"
    description = "Verifies the embedded macOS helper structure."
    dependsOn(signMacOsHelperApp)
    requiredFiles.from(
        "$macOsHelperApplication/Contents/Info.plist",
        "$macOsHelperApplication/Contents/Library/LaunchDaemons/app.posato.macos.proxy-settings.plist",
        macOsDaemonExecutable,
        "$macOsHelperApplication/Contents/MacOS/PosatoMacOSHelper",
    )
    helperInfoPlist.set(file("$macOsHelperApplication/Contents/Info.plist"))
    daemonPlist.set(
        file(
            "$macOsHelperApplication/Contents/Library/LaunchDaemons/" +
                "app.posato.macos.proxy-settings.plist",
        ),
    )
}

val verifyMacOsHelperPackaging by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies the signed application and embedded macOS helper chain."
    dependsOn(verifyMacOsHelperStructure)

    commandLine(
        "/usr/bin/codesign",
        "--verify",
        "--deep",
        "--strict",
        macOsApplication,
    )
}

tasks.matching { it.name == "packageDmg" }.configureEach {
    dependsOn(signMacOsHelperApp)
}
