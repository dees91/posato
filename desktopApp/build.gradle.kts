import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.tasks.AbstractNativeMacApplicationPackageDmgTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

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

abstract class VerifyMacOsDevelopmentPackaging : DefaultTask() {
    @get:Internal
    abstract val applicationBundle: DirectoryProperty

    @get:Input
    abstract val signingIdentity: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun verify() {
        val application = applicationBundle.get().asFile
        val helper = application.resolve("Contents/Helpers/PosatoMacOSHelper.app")
        val daemon = helper.resolve("Contents/Resources/PosatoProxySettingsDaemon")
        val runtime = application.resolve("Contents/runtime")
        val applicationCode = application.resolve("Contents/app")
        val launcher = application.resolve("Contents/MacOS/Posato")
        val sqliteLibrary = extractSqliteLibrary(applicationCode)

        command("/usr/bin/codesign", "--verify", "--deep", "--strict", application.absolutePath)

        val applicationSignature = signature(application)
        check(applicationSignature.identifier == "app.posato.macos")
        check(signature(helper).identifier == "app.posato.macos.helper")
        check(signature(daemon).identifier == "app.posato.macos.proxy-settings")
        check(entitlements(helper).isEmpty())
        check(entitlements(daemon).isEmpty())

        val signedCode = buildList {
            add(application)
            add(launcher)
            add(runtime)
            addAll(machOFiles(runtime))
            addAll(machOFiles(applicationCode, recursive = false))
            add(helper)
            add(daemon)
            add(sqliteLibrary)
        }
        val signatures = signedCode.map(::signature)
        val expectedApplicationEntitlements = if (signingIdentity.get() == "-") {
            signatures.forEach { codeSignature ->
                check(codeSignature.isAdHoc)
                check(codeSignature.teamId == null)
                check(codeSignature.authorities.isEmpty())
            }
            AD_HOC_APPLICATION_ENTITLEMENTS
        } else {
            val expectedTeamId = applicationSignature.teamId
            check(!expectedTeamId.isNullOrBlank())
            signatures.forEach { codeSignature ->
                check(!codeSignature.isAdHoc)
                check(codeSignature.teamId == expectedTeamId)
                check(codeSignature.authorities.firstOrNull()?.startsWith("Apple Development:") == true)
            }
            DEVELOPMENT_APPLICATION_ENTITLEMENTS
        }
        check(entitlements(application) == expectedApplicationEntitlements)
        check(entitlements(launcher) == expectedApplicationEntitlements)
        signedCode.drop(2).forEach { code ->
            check(entitlements(code).isEmpty())
        }
    }

    private fun extractSqliteLibrary(applicationCode: File): File {
        val sqliteJar = applicationCode.listFiles()
            .orEmpty()
            .filter { file -> file.name.startsWith("sqlite-jdbc-") && file.extension == "jar" }
            .singleOrNull()
            ?: throw GradleException("The packaged arm64 SQLite JDBC archive is missing or ambiguous.")
        val sqliteLibrary = temporaryDir.resolve(SQLITE_LIBRARY_PATH)
        sqliteLibrary.parentFile.mkdirs()
        ZipFile(sqliteJar).use { archive ->
            val entry = archive.getEntry(SQLITE_LIBRARY_PATH)
                ?: throw GradleException("The packaged arm64 SQLite JDBC library is missing.")
            archive.getInputStream(entry).use { input ->
                sqliteLibrary.outputStream().use(input::copyTo)
            }
        }
        return sqliteLibrary
    }

    private fun machOFiles(
        directory: File,
        recursive: Boolean = true,
    ): List<File> {
        val files = if (recursive) directory.walkTopDown().asSequence() else directory.listFiles().orEmpty().asSequence()
        return files
            .filter { file -> file.isFile && !Files.isSymbolicLink(file.toPath()) }
            .filter { file -> command("/usr/bin/file", "--brief", file.absolutePath).contains("Mach-O") }
            .sortedBy(File::getAbsolutePath)
            .toList()
    }

    private fun signature(code: File): CodeSignature {
        val details = command("/usr/bin/codesign", "--display", "--verbose=4", code.absolutePath)
        val values = details.lineSequence().map(String::trim).toList()
        val teamId = values.firstOrNull { line -> line.startsWith("TeamIdentifier=") }
            ?.substringAfter('=')
            ?.takeUnless { value -> value == "not set" }
        return CodeSignature(
            identifier = values.firstOrNull { line -> line.startsWith("Identifier=") }?.substringAfter('=')
                ?: throw GradleException("Packaged code has no signing identifier."),
            teamId = teamId,
            authorities = values.filter { line -> line.startsWith("Authority=") }.map { line -> line.substringAfter('=') },
            isAdHoc = values.any { line -> line == "Signature=adhoc" },
        )
    }

    private fun entitlements(code: File): Map<String, Boolean> {
        val output = command("/usr/bin/codesign", "--display", "--entitlements", ":-", code.absolutePath)
        val start = output.indexOf("<plist")
        val end = output.indexOf("</plist>")
        if (start == -1 || end == -1) {
            return emptyMap()
        }
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(output.substring(start, end + "</plist>".length).byteInputStream())
        val entries = document.getElementsByTagName("dict").item(0).childNodes
            .let { children -> (0 until children.length).map(children::item) }
            .filter { node -> node.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
        check(entries.size % 2 == 0)
        return entries.chunked(2).associate { (key, value) ->
            check(key.nodeName == "key")
            check(value.nodeName == "true" || value.nodeName == "false")
            key.textContent to (value.nodeName == "true")
        }
    }

    private fun command(vararg arguments: String): String {
        val standardOutput = ByteArrayOutputStream()
        val errorOutput = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(*arguments)
            this.standardOutput = standardOutput
            this.errorOutput = errorOutput
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException("macOS package verification failed in ${arguments.first()}.")
        }
        return standardOutput.toString(Charsets.UTF_8) + errorOutput.toString(Charsets.UTF_8)
    }

    private data class CodeSignature(
        val identifier: String,
        val teamId: String?,
        val authorities: List<String>,
        val isAdHoc: Boolean,
    )

    private companion object {
        const val SQLITE_LIBRARY_PATH = "org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib"

        val DEVELOPMENT_APPLICATION_ENTITLEMENTS = mapOf("com.apple.security.cs.allow-jit" to true)
        val AD_HOC_APPLICATION_ENTITLEMENTS = mapOf(
            "com.apple.security.cs.allow-jit" to true,
            "com.apple.security.cs.allow-unsigned-executable-memory" to true,
            "com.apple.security.cs.disable-library-validation" to true,
        )
    }
}

abstract class SignMacOsDevelopmentPackage : DefaultTask() {
    @get:Internal
    abstract val applicationBundle: DirectoryProperty

    @get:InputFile
    abstract val developmentEntitlements: RegularFileProperty

    @get:Input
    abstract val signingIdentity: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun sign() {
        val application = applicationBundle.get().asFile
        val helper = application.resolve("Contents/Helpers/PosatoMacOSHelper.app")
        val daemon = helper.resolve("Contents/Resources/PosatoProxySettingsDaemon")
        val runtime = application.resolve("Contents/runtime")
        val applicationCode = application.resolve("Contents/app")
        val identity = signingIdentity.get()

        if (identity == "-") {
            signSqliteLibrary(applicationCode, identity)
            machOFiles(runtime).forEach { code -> signCode(code, identity) }
            signCode(runtime, identity)
            machOFiles(applicationCode, recursive = false).forEach { code -> signCode(code, identity) }
            signCode(daemon, identity, identifier = "app.posato.macos.proxy-settings")
            signCode(helper, identity, identifier = "app.posato.macos.helper")
            signCode(application, identity, preserveEntitlements = true)
            return
        }

        signSqliteLibrary(applicationCode, identity)
        machOFiles(runtime).forEach { code -> signCode(code, identity) }
        signCode(runtime, identity)
        machOFiles(applicationCode, recursive = false).forEach { code -> signCode(code, identity) }
        signCode(daemon, identity, identifier = "app.posato.macos.proxy-settings")
        signCode(helper, identity, identifier = "app.posato.macos.helper")
        signCode(
            application,
            identity,
            identifier = "app.posato.macos",
            entitlements = developmentEntitlements.get().asFile,
        )
    }

    private fun signSqliteLibrary(
        applicationCode: File,
        identity: String,
    ) {
        val sqliteJar = applicationCode.listFiles()
            .orEmpty()
            .filter { file -> file.name.startsWith("sqlite-jdbc-") && file.extension == "jar" }
            .singleOrNull()
            ?: throw GradleException("The packaged arm64 SQLite JDBC archive is missing or ambiguous.")
        FileSystems.newFileSystem(sqliteJar.toPath()).use { archive ->
            val library = archive.getPath(SQLITE_LIBRARY_PATH)
            if (!Files.isRegularFile(library)) {
                throw GradleException("The packaged arm64 SQLite JDBC library is missing.")
            }
            val stagedLibrary = temporaryDir.resolve(SQLITE_LIBRARY_PATH)
            stagedLibrary.parentFile.mkdirs()
            Files.copy(library, stagedLibrary.toPath(), StandardCopyOption.REPLACE_EXISTING)
            signCode(stagedLibrary, identity)
            Files.copy(stagedLibrary.toPath(), library, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun machOFiles(
        directory: File,
        recursive: Boolean = true,
    ): List<File> {
        val files = if (recursive) directory.walkTopDown().asSequence() else directory.listFiles().orEmpty().asSequence()
        return files
            .filter { file -> file.isFile && !Files.isSymbolicLink(file.toPath()) }
            .filter { file -> command("/usr/bin/file", "--brief", file.absolutePath).contains("Mach-O") }
            .sortedBy(File::getAbsolutePath)
            .toList()
    }

    private fun signCode(
        code: File,
        identity: String,
        identifier: String? = null,
        entitlements: File? = null,
        preserveEntitlements: Boolean = false,
    ) {
        val arguments = mutableListOf(
            "/usr/bin/codesign",
            "--force",
            "--options",
            "runtime",
            "--timestamp=none",
        )
        identifier?.let { value -> arguments += listOf("--identifier", value) }
        entitlements?.let { file -> arguments += listOf("--entitlements", file.absolutePath) }
        if (preserveEntitlements) {
            arguments += "--preserve-metadata=entitlements"
        }
        arguments += listOf("--sign", identity, code.absolutePath)
        command(*arguments.toTypedArray())
    }

    private fun command(vararg arguments: String): String {
        val standardOutput = ByteArrayOutputStream()
        val errorOutput = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(*arguments)
            this.standardOutput = standardOutput
            this.errorOutput = errorOutput
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException("macOS package signing failed in ${arguments.first()}.")
        }
        return standardOutput.toString(Charsets.UTF_8) + errorOutput.toString(Charsets.UTF_8)
    }

    private companion object {
        const val SQLITE_LIBRARY_PATH = "org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib"
    }
}

val macOsHelperBundle = project(":macosHelper").layout.buildDirectory
    .dir("bundle/PosatoMacOSHelper.app")
val macOsDistributable = layout.buildDirectory.dir(
    "compose/binaries/main/app/Posato.app",
)
val macOsDevelopmentPackageRoot = layout.buildDirectory.dir(
    "compose/binaries/main/development-package",
)
val macOsDevelopmentApplication = macOsDevelopmentPackageRoot.map { directory -> directory.dir("Posato.app") }
val macOsSigningIdentity = providers.gradleProperty("posatoMacOsSigningIdentity")
    .orElse("-")
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

val signMacOsDevelopmentPackage by tasks.registering(SignMacOsDevelopmentPackage::class) {
    group = "build"
    description = "Signs the generated macOS application and its nested code inside-out."
    dependsOn(embedMacOsHelper)
    applicationBundle.set(macOsDistributable)
    developmentEntitlements.set(layout.projectDirectory.file("Config/PosatoDevelopment.entitlements"))
    signingIdentity.set(macOsSigningIdentity)
    outputs.upToDateWhen { false }
}

val verifyMacOsHelperStructure by tasks.registering(VerifyMacOsHelperStructure::class) {
    group = "verification"
    description = "Verifies the embedded macOS helper structure."
    dependsOn(signMacOsDevelopmentPackage)
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

val stageMacOsDevelopmentPackage by tasks.registering(Sync::class) {
    group = "build"
    description = "Stages the verified signed application for macOS packaging."
    dependsOn(verifyMacOsHelperStructure)

    from(macOsDistributable)
    into(macOsDevelopmentApplication)
}

val verifyMacOsDevelopmentPackaging by tasks.registering(VerifyMacOsDevelopmentPackaging::class) {
    group = "verification"
    description = "Verifies the development-signed macOS application and nested code."
    dependsOn(stageMacOsDevelopmentPackage)
    applicationBundle.set(macOsDevelopmentApplication)
    signingIdentity.set(macOsSigningIdentity)
}

tasks.register<AbstractNativeMacApplicationPackageDmgTask>("packageDmg") {
    group = "distribution"
    description = "Packages the verified development-signed macOS application as a DMG."
    dependsOn(verifyMacOsDevelopmentPackaging)
    packageName.set("Posato")
    packageVersion.set("1.0.0")
    destinationDir.set(layout.buildDirectory.dir("compose/binaries/main/dmg"))
    appDir.set(macOsDevelopmentPackageRoot)
}

tasks.matching { it.name == "runDistributable" }.configureEach {
    dependsOn(verifyMacOsDevelopmentPackaging)
}
