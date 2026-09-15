import app.posato.buildlogic.PosatoPaths
import app.posato.buildlogic.PosatoVersion
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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.tasks.AbstractNativeMacApplicationPackageDmgTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.zip.ZipEntry
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

    @get:Input
    abstract val release: Property<Boolean>

    @get:Input
    abstract val marketingVersion: Property<String>

    @get:Input
    abstract val buildNumber: Property<String>

    @get:InputFile
    abstract val iconFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    abstract val runtimeSourceRelease: RegularFileProperty

    @get:InputFiles
    abstract val noticeFiles: ConfigurableFileCollection

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun verify() {
        val identity = signingIdentity.get()
        check(!release.get() || identity.startsWith("Developer ID Application:") || identity.matches(Regex("[0-9A-F]{40}"))) {
            "A macOS release needs -PposatoMacOsReleaseSigningIdentity with a Developer ID Application identity name or SHA-1 hash."
        }
        val application = applicationBundle.get().asFile
        val helper = application.resolve("Contents/Helpers/PosatoMacOSHelper.app")
        val daemon = helper.resolve("Contents/Resources/PosatoProxySettingsDaemon")
        val companion = application.resolve("Contents/Helpers/PosatoMacOSSync.app")
        val companionExecutable = companion.resolve("Contents/MacOS/PosatoMacOSSync")
        val runtime = application.resolve("Contents/runtime")
        val applicationCode = application.resolve("Contents/app")
        val launcher = application.resolve("Contents/MacOS/Posato")
        val archivedNativeLibraries = nativeLibrariesInArchives(applicationCode)
        val windowLibrary = applicationCode.resolve("resources/native/libPosatoWindow.dylib")
        check(windowLibrary.isFile) { "The packaged window chrome library is missing." }

        command("/usr/bin/codesign", "--verify", "--deep", "--strict", application.absolutePath)

        val applicationSignature = signature(application)
        check(applicationSignature.identifier == "app.posato.macos")
        check(signature(helper).identifier == "app.posato.macos.helper")
        check(signature(daemon).identifier == "app.posato.macos.proxy-settings")
        check(signature(companion).identifier == "app.posato.macos.sync")
        check(signature(companionExecutable).identifier == "app.posato.macos.sync")
        check(entitlements(helper).isEmpty())
        check(entitlements(daemon).isEmpty())
        verifyCompanionEntitlements(companion, signingIdentity.get())

        val signedCode = buildList {
            add(application)
            add(launcher)
            add(runtime)
            addAll(machOFiles(runtime))
            addAll(machOFiles(applicationCode, recursive = false))
            add(helper)
            add(daemon)
            add(companion)
            add(companionExecutable)
            addAll(archivedNativeLibraries)
            add(windowLibrary)
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
            val expectedAuthority = if (release.get()) "Developer ID Application:" else "Apple Development:"
            signatures.forEach { codeSignature ->
                check(!codeSignature.isAdHoc)
                check(codeSignature.teamId == expectedTeamId)
                check(codeSignature.authorities.firstOrNull()?.startsWith(expectedAuthority) == true)
                check(codeSignature.hasHardenedRuntime)
                check(codeSignature.hasSecureTimestamp == release.get())
            }
            DEVELOPMENT_APPLICATION_ENTITLEMENTS
        }
        check(entitlements(application) == expectedApplicationEntitlements)
        check(entitlements(launcher) == expectedApplicationEntitlements)
        signedCode.drop(2).filterNot { code ->
            code == companion || code == companionExecutable
        }.forEach { code ->
            check(entitlements(code).isEmpty())
        }
        listOf(application, helper, companion).forEach { bundle ->
            val info = bundle.resolve("Contents/Info.plist")
            check(plistValue(info, "CFBundleShortVersionString") == marketingVersion.get())
            check(plistValue(info, "CFBundleVersion") == buildNumber.get())
        }
        check(application.resolve("Contents/Resources/Posato.icns").readBytes().contentEquals(iconFile.get().asFile.readBytes()))
        verifyBundledNotices(applicationCode)
        if (release.get()) {
            verifyReleaseRuntime(runtime)
            verifyDeveloperIdProfile(companion, applicationSignature.teamId.orEmpty())
        }
    }

    private fun verifyBundledNotices(applicationCode: File) {
        val notices = noticeFiles.files.sortedBy(File::getName)
        check(notices.map(File::getName) == listOf("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"))
        val sharedJar = applicationCode.listFiles()
            .orEmpty()
            .singleOrNull { file -> file.name.startsWith("shared-jvm-") && file.extension == "jar" }
            ?: throw GradleException("The packaged shared archive is missing or ambiguous.")
        ZipFile(sharedJar).use { archive ->
            notices.forEach { notice ->
                val entry = archive.getEntry("$NOTICE_RESOURCE_DIRECTORY/${notice.name}")
                    ?: throw GradleException("The packaged ${notice.name} notice is missing.")
                val packaged = archive.getInputStream(entry).use { input -> input.readBytes() }
                check(packaged.contentEquals(notice.readBytes())) { "The packaged ${notice.name} notice differs from the repository file." }
            }
        }
    }

    private fun verifyReleaseRuntime(runtime: File) {
        val source = runtimeSourceRelease.orNull?.asFile
            ?: throw GradleException("The release runtime source JDK is not configured.")
        val sourceValues = releaseValues(source)
        val bundledValues = releaseValues(runtime.resolve("Contents/Home/release"))
        check(sourceValues["IMPLEMENTOR"] == "\"Eclipse Adoptium\"")
        check(sourceValues["JAVA_VERSION"] != null && sourceValues["JAVA_VERSION"] == bundledValues["JAVA_VERSION"])
        check(runtime.resolve("Contents/Home/legal").isDirectory)
    }

    private fun releaseValues(file: File): Map<String, String> {
        return file.readLines()
            .filter { line -> line.contains('=') }
            .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
    }

    private fun verifyDeveloperIdProfile(
        companion: File,
        teamId: String,
    ) {
        val embeddedProfile = companion.resolve("Contents/embedded.provisionprofile")
        val decoded = command("/usr/bin/security", "cms", "-D", "-i", embeddedProfile.absolutePath)
        val start = decoded.indexOf("<plist")
        val end = decoded.indexOf("</plist>")
        check(start != -1 && end != -1) { "The embedded companion profile cannot be decoded." }
        val profile = plistObject(plistRootDictionary(decoded.substring(start, end + "</plist>".length))) as Map<*, *>
        check(profile["ProvisionsAllDevices"] == true) { "The embedded companion profile is not a Developer ID profile." }
        check(profile["ProvisionedDevices"] == null) { "The embedded companion profile lists devices." }
        check(profile["TeamIdentifier"] == listOf(teamId)) { "The embedded companion profile belongs to another team." }
        check(Instant.parse(profile["ExpirationDate"] as String).isAfter(Instant.now())) { "The embedded companion profile has expired." }
        val allowedEntitlements = profile["Entitlements"] as Map<*, *>
        entitlementEntries(companion).forEach { (key, value) ->
            check(isAllowedEntitlement(allowedEntitlements[key], plistObject(value))) {
                "The embedded companion profile does not allow the signed $key entitlement."
            }
        }
    }

    private fun isAllowedEntitlement(
        allowed: Any?,
        signed: Any?,
    ): Boolean {
        return when {
            allowed == "*" -> true
            signed is List<*> -> signed.isNotEmpty() && signed.all { value -> isAllowedEntitlement(allowed, value) }
            allowed is List<*> -> allowed.any { candidate -> isAllowedEntitlement(candidate, signed) }
            allowed is String && signed is String && allowed.endsWith("*") -> signed.startsWith(allowed.removeSuffix("*"))
            else -> allowed != null && allowed == signed
        }
    }

    private fun plistValue(
        file: File,
        key: String,
    ): String {
        return command("/usr/libexec/PlistBuddy", "-c", "Print :$key", file.absolutePath).trim()
    }

    private fun verifyCompanionEntitlements(
        companion: File,
        identity: String,
    ) {
        val arrays = stringArrayEntitlements(companion)
        if (identity == "-") {
            check(entitlements(companion).isEmpty())
            check(arrays.isEmpty())
            return
        }
        check(entitlements(companion).isEmpty())
        check(arrays["com.apple.developer.icloud-container-identifiers"] == listOf("iCloud.app.posato.sync"))
        check(arrays["com.apple.developer.icloud-services"] == listOf("CloudKit"))
        val groups = arrays["keychain-access-groups"].orEmpty()
        check(groups.size == 1)
        check(groups.single().endsWith(".app.posato.sync"))
        val applicationIdentifier = stringEntitlements(companion)["com.apple.application-identifier"]
        check(applicationIdentifier != null)
        check(applicationIdentifier.endsWith(".app.posato.macos.sync"))
        check(applicationIdentifier.removeSuffix(".app.posato.macos.sync").length == 10)
        val containerEnvironment = stringEntitlements(companion)["com.apple.developer.icloud-container-environment"]
        check(containerEnvironment == if (release.get()) "Production" else null)
    }

    private fun nativeLibrariesInArchives(applicationCode: File): List<File> {
        val jars = applicationCode.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.extension == "jar" }
            .sortedBy(File::getName)
        val sqliteJar = jars.singleOrNull { jar -> jar.name.startsWith("sqlite-jdbc-") }
            ?: throw GradleException("The packaged arm64 SQLite JDBC archive is missing or ambiguous.")
        val libraries = jars.flatMap { jar ->
            ZipFile(jar).use { archive ->
                archive.entries().asSequence()
                    .filter { entry -> !entry.isDirectory && !entry.name.endsWith(".class") }
                    .filter { entry -> archive.getInputStream(entry).use { input -> input.readNBytes(4) }.isMachOMagic() }
                    .map { entry -> extractEntry(archive, jar, entry) }
                    .filter { file -> command("/usr/bin/file", "--brief", file.absolutePath).contains("Mach-O") }
                    .toList()
            }
        }
        val sqliteLibrary = temporaryDir.resolve(sqliteJar.nameWithoutExtension).resolve(SQLITE_LIBRARY_PATH)
        check(sqliteLibrary in libraries) { "The packaged arm64 SQLite JDBC library is missing." }
        return libraries
    }

    private fun extractEntry(
        archive: ZipFile,
        jar: File,
        entry: ZipEntry,
    ): File {
        val extracted = temporaryDir.resolve(jar.nameWithoutExtension).resolve(entry.name)
        extracted.parentFile.mkdirs()
        archive.getInputStream(entry).use { input ->
            extracted.outputStream().use(input::copyTo)
        }
        return extracted
    }

    private fun ByteArray.isMachOMagic(): Boolean {
        return size == 4 && toList() in MACH_O_MAGIC_NUMBERS
    }

    private fun plistRootDictionary(plist: String): org.w3c.dom.Element {
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(plist.byteInputStream())
        return document.getElementsByTagName("dict").item(0) as org.w3c.dom.Element
    }

    private fun elementChildren(element: org.w3c.dom.Element): List<org.w3c.dom.Element> {
        return element.childNodes
            .let { children -> (0 until children.length).map(children::item) }
            .filterIsInstance<org.w3c.dom.Element>()
    }

    private fun plistObject(element: org.w3c.dom.Element): Any? {
        return when (element.nodeName) {
            "dict" -> elementChildren(element).chunked(2).associate { (key, value) -> key.textContent to plistObject(value) }
            "array" -> elementChildren(element).map(::plistObject)
            "true" -> true
            "false" -> false
            else -> element.textContent
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

    private fun signature(code: File): CodeSignature {
        command("/usr/bin/codesign", "--verify", "--strict", code.absolutePath)
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
            hasSecureTimestamp = values.any { line -> line.startsWith("Timestamp=") },
            hasHardenedRuntime = values.any { line -> line.startsWith("CodeDirectory ") && line.contains("runtime") },
        )
    }

    private fun entitlements(code: File): Map<String, Boolean> {
        return entitlementEntries(code).mapNotNull { (key, value) ->
            when (value.nodeName) {
                "true", "false" -> key to (value.nodeName == "true")
                "array", "string" -> null
                else -> error("Unsupported entitlement value ${value.nodeName}")
            }
        }.toMap()
    }

    private fun stringEntitlements(code: File): Map<String, String> {
        return entitlementEntries(code).mapNotNull { (key, value) ->
            if (value.nodeName == "string") {
                key to value.textContent
            } else {
                null
            }
        }.toMap()
    }

    private fun stringArrayEntitlements(code: File): Map<String, List<String>> {
        return entitlementEntries(code).mapNotNull { (key, value) ->
            if (value.nodeName != "array") {
                null
            } else {
                val values = value.childNodes
                    .let { children -> (0 until children.length).map(children::item) }
                    .filter { node -> node.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
                    .map { node ->
                        check(node.nodeName == "string")
                        node.textContent
                    }
                key to values
            }
        }.toMap()
    }

    private fun entitlementEntries(code: File): List<Pair<String, org.w3c.dom.Element>> {
        val output = command("/usr/bin/codesign", "--display", "--entitlements", ":-", code.absolutePath)
        val start = output.indexOf("<plist")
        val end = output.indexOf("</plist>")
        if (start == -1 || end == -1) {
            return emptyList()
        }
        val entries = elementChildren(plistRootDictionary(output.substring(start, end + "</plist>".length)))
        check(entries.size % 2 == 0)
        return entries.chunked(2).map { (key, value) ->
            check(key.nodeName == "key")
            check(value is org.w3c.dom.Element)
            key.textContent to value
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
        val hasSecureTimestamp: Boolean,
        val hasHardenedRuntime: Boolean,
    )

    private companion object {
        const val SQLITE_LIBRARY_PATH = "org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib"
        const val NOTICE_RESOURCE_DIRECTORY = "composeResources/app.posato.generated.resources/files/legal"

        val MACH_O_MAGIC_NUMBERS = listOf(
            listOf(0xCF, 0xFA, 0xED, 0xFE),
            listOf(0xCE, 0xFA, 0xED, 0xFE),
            listOf(0xFE, 0xED, 0xFA, 0xCF),
            listOf(0xFE, 0xED, 0xFA, 0xCE),
            listOf(0xCA, 0xFE, 0xBA, 0xBE),
            listOf(0xBE, 0xBA, 0xFE, 0xCA),
        ).map { bytes -> bytes.map(Int::toByte) }

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

    @get:InputFile
    abstract val companionEntitlementsTemplate: RegularFileProperty

    @get:InputFile
    @get:Optional
    abstract val companionProvisioningProfile: RegularFileProperty

    @get:Input
    abstract val signingIdentity: Property<String>

    @get:Input
    abstract val release: Property<Boolean>

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
        if (release.get() && !(identity.startsWith("Developer ID Application:") || identity.matches(Regex("[0-9A-F]{40}")))) {
            throw GradleException(
                "A macOS release needs -PposatoMacOsReleaseSigningIdentity with a Developer ID Application identity name or SHA-1 hash.",
            )
        }
        val windowLibrary = applicationCode.resolve("resources/native/libPosatoWindow.dylib")
        check(windowLibrary.isFile) { "The packaged window chrome library is missing." }
        removeForeignNativeLibraries(applicationCode)
        signCode(windowLibrary, identity)

        if (identity == "-") {
            signSqliteLibrary(applicationCode, identity)
            machOFiles(runtime).forEach { code -> signCode(code, identity) }
            signCode(runtime, identity)
            machOFiles(applicationCode, recursive = false).forEach { code -> signCode(code, identity) }
            signCode(daemon, identity, identifier = "app.posato.macos.proxy-settings")
            signCode(helper, identity, identifier = "app.posato.macos.helper")
            signCompanion(application, identity, entitlements = null)
            signCode(application, identity, preserveEntitlements = true)
            return
        }

        signSqliteLibrary(applicationCode, identity)
        machOFiles(runtime).forEach { code -> signCode(code, identity) }
        signCode(runtime, identity)
        machOFiles(applicationCode, recursive = false).forEach { code -> signCode(code, identity) }
        signCode(daemon, identity, identifier = "app.posato.macos.proxy-settings")
        signCode(helper, identity, identifier = "app.posato.macos.helper")
        signCompanion(application, identity, entitlements = companionEntitlements())
        signCode(
            application,
            identity,
            identifier = "app.posato.macos",
            entitlements = developmentEntitlements.get().asFile,
        )
    }

    private fun signCompanion(
        application: File,
        identity: String,
        entitlements: File?,
    ) {
        val companion = application.resolve("Contents/Helpers/PosatoMacOSSync.app")
        val executable = companion.resolve("Contents/MacOS/PosatoMacOSSync")
        signCode(executable, identity, identifier = "app.posato.macos.sync", entitlements = entitlements)
        signCode(companion, identity, identifier = "app.posato.macos.sync", entitlements = entitlements)
    }

    private fun companionEntitlements(): File {
        val profile = companionProvisioningProfile.orNull?.asFile
            ?: throw GradleException(
                if (release.get()) {
                    "A macOS release needs an untracked Developer ID profile for app.posato.macos.sync. " +
                        "Set posatoMacOsSyncDeveloperIdProfile to that file."
                } else {
                    "Apple Development packaging needs an untracked development profile for " +
                        "app.posato.macos.sync (iCloud/CloudKit). Set " +
                        "posatoMacOsSyncProvisioningProfile to that file."
                },
            )
        val teamPrefix = teamIdentifier(profile)
        val entitlements = temporaryDir.resolve("PosatoMacOSSync.entitlements")
        val templateEntitlements = companionEntitlementsTemplate.get().asFile.readText()
            .replace("__APP_IDENTIFIER_PREFIX__", teamPrefix)
        entitlements.writeText(
            if (release.get()) {
                templateEntitlements.replace("</dict>", PRODUCTION_CONTAINER_ENVIRONMENT + "</dict>")
            } else {
                templateEntitlements
            },
        )
        val embedded = applicationBundle.get().asFile
            .resolve("Contents/Helpers/PosatoMacOSSync.app/Contents/embedded.provisionprofile")
        Files.copy(profile.toPath(), embedded.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return entitlements
    }

    private fun teamIdentifier(profile: File): String {
        val decoded = command("/usr/bin/security", "cms", "-D", "-i", profile.absolutePath)
        val match = Regex(
            "<key>TeamIdentifier</key>\\s*<array>\\s*<string>([A-Z0-9]{10})</string>",
        ).find(decoded)
            ?: throw GradleException("The companion provisioning profile is missing a TeamIdentifier array.")
        return match.groupValues[1] + "."
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
            if (release.get()) "--timestamp" else "--timestamp=none",
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

    private fun removeForeignNativeLibraries(applicationCode: File) {
        applicationCode.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.extension == "jar" }
            .forEach { jar ->
                FileSystems.newFileSystem(jar.toPath()).use { archive ->
                    FOREIGN_NATIVE_LIBRARY_PATHS
                        .map { path -> archive.getPath(path) }
                        .filter { path -> Files.isRegularFile(path) }
                        .forEach(Files::delete)
                }
            }
    }

    private companion object {
        const val SQLITE_LIBRARY_PATH = "org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib"
        const val PRODUCTION_CONTAINER_ENVIRONMENT =
            "    <key>com.apple.developer.icloud-container-environment</key>\n    <string>Production</string>\n"

        val FOREIGN_NATIVE_LIBRARY_PATHS = listOf(
            "libskiko-macos-x64.dylib",
            "org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib",
        )
    }
}

abstract class NotarizeMacOsArtifact : DefaultTask() {
    @get:Internal
    abstract val artifact: Property<File>

    @get:Internal
    abstract val keyId: Property<String>

    @get:Internal
    abstract val issuerId: Property<String>

    @get:Internal
    abstract val privateKey: Property<String>

    @get:Internal
    abstract val releaseSigningIdentity: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun notarize() {
        val configuredArtifact = artifact.get()
        val target = if (configuredArtifact.isDirectory && configuredArtifact.extension != "app") {
            configuredArtifact.listFiles().orEmpty().singleOrNull { file -> file.extension == "dmg" }
                ?: throw GradleException("Expected exactly one DMG in ${configuredArtifact.name}.")
        } else {
            configuredArtifact
        }
        val privateKeyFile = File(PosatoPaths.expandHome(requiredValue(privateKey, "posatoAscPrivateKeyPath")))
        check(privateKeyFile.isFile) { "The App Store Connect private key file is missing." }
        val credentials = arrayOf(
            "--key",
            privateKeyFile.absolutePath,
            "--key-id",
            requiredValue(keyId, "posatoAscKeyId"),
            "--issuer",
            requiredValue(issuerId, "posatoAscIssuerId"),
        )
        val submission = if (target.isDirectory) {
            val archive = temporaryDir.resolve("${target.nameWithoutExtension}.zip")
            archive.delete()
            run("/usr/bin/ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", target.absolutePath, archive.absolutePath).requireSuccess()
            archive
        } else {
            val identity = requiredValue(releaseSigningIdentity, "posatoMacOsReleaseSigningIdentity")
            run("/usr/bin/codesign", "--force", "--timestamp", "--sign", identity, target.absolutePath).requireSuccess()
            target
        }
        val result = run(
            "/usr/bin/xcrun",
            "notarytool",
            "submit",
            submission.absolutePath,
            *credentials,
            "--wait",
            "--output-format",
            "json",
        )
        temporaryDir.resolve("submission.json").writeText(result.output)
        val submissionId = SUBMISSION_ID.find(result.output)?.groupValues?.get(1)
            ?: throw GradleException("Notarization returned no submission identifier; see ${temporaryDir.resolve("submission.json")}.")
        if (SUBMISSION_STATUS.find(result.output)?.groupValues?.get(1) != "Accepted") {
            val log = temporaryDir.resolve("notarization-log.json")
            run("/usr/bin/xcrun", "notarytool", "log", submissionId, *credentials, log.absolutePath)
            throw GradleException("Notarization of ${target.name} was not accepted; see $log.")
        }
        run("/usr/bin/xcrun", "stapler", "staple", target.absolutePath).requireSuccess()
        run("/usr/bin/xcrun", "stapler", "validate", target.absolutePath).requireSuccess()
        val assessment = if (target.isDirectory) {
            run("/usr/sbin/spctl", "--assess", "--type", "execute", "--verbose=2", target.absolutePath)
        } else {
            run("/usr/sbin/spctl", "--assess", "--type", "open", "--context", "context:primary-signature", "--verbose=2", target.absolutePath)
        }
        assessment.requireSuccess()
        check(assessment.output.contains("source=Notarized Developer ID")) {
            "Gatekeeper did not accept ${target.name} as notarized Developer ID code."
        }
    }

    private fun requiredValue(
        property: Property<String>,
        name: String,
    ): String {
        return property.orNull?.takeIf(String::isNotBlank)
            ?: throw GradleException("A macOS release needs -P$name.")
    }

    private fun run(vararg arguments: String): CommandResult {
        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(*arguments)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        return CommandResult(arguments.take(2).joinToString(" "), result.exitValue, output.toString(Charsets.UTF_8))
    }

    private data class CommandResult(
        val command: String,
        val exitValue: Int,
        val output: String,
    ) {
        fun requireSuccess() {
            if (exitValue != 0) {
                throw GradleException("macOS release step failed in $command.")
            }
        }
    }

    private companion object {
        val SUBMISSION_ID = Regex(""""id"\s*:\s*"([0-9a-fA-F-]{36})"""")
        val SUBMISSION_STATUS = Regex(""""status"\s*:\s*"([A-Za-z ]+)"""")
    }
}

val macOsHelperBundle = project(":macosHelper").layout.buildDirectory
    .dir("bundle/PosatoMacOSHelper.app")
val macOsSyncCompanionBundle = project(":macosSyncCompanion").layout.buildDirectory
    .dir("bundle/PosatoMacOSSync.app")
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
val posatoMarketingVersion = PosatoVersion.marketingVersion(rootProject.file("Version.xcconfig"))
val posatoBuildNumber = PosatoVersion.developmentBuildNumber(providers.gradleProperty("posatoMacOsBuildNumber").orNull)

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

val windowChromeResources = layout.buildDirectory.dir("generated/window-chrome")
val windowChromeLibrary = windowChromeResources.map { it.file("macos-arm64/native/libPosatoWindow.dylib") }
val posatoJavaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.ADOPTIUM)
}
val compileWindowChrome = tasks.register<Exec>("compileWindowChrome") {
    val source = layout.projectDirectory.file("src/main/objc/WindowChrome.m")
    val javaInstallation = posatoJavaLauncher.get().metadata.installationPath.asFile
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

tasks.withType<AbstractJPackageTask>().configureEach {
    inputs.file(compileWindowChrome.map { windowChromeLibrary.get() })
        .withPropertyName("windowChrome")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

compose.desktop {
    application {
        mainClass = "app.posato.desktop.MainKt"
        javaHome = posatoJavaLauncher.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            appResourcesRootDir.set(compileWindowChrome.map { windowChromeResources.get() })
            packageName = "Posato"
            packageVersion = posatoMarketingVersion
            modules("java.sql")

            macOS {
                bundleID = "app.posato.macos"
                minimumSystemVersion = "15.0"
                packageBuildVersion = posatoBuildNumber
                iconFile.set(layout.projectDirectory.file("Config/Posato.icns"))
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

val embedMacOsSyncCompanion by tasks.registering(Sync::class) {
    group = "build"
    description = "Embeds the native macOS synchronization companion in the distributable."
    dependsOn(":macosSyncCompanion:assembleCompanionBundle", "createDistributable")

    from(macOsSyncCompanionBundle)
    into(
        macOsDistributable.map { app ->
            app.dir("Contents/Helpers/PosatoMacOSSync.app")
        },
    )
}

val signMacOsDevelopmentPackage by tasks.registering(SignMacOsDevelopmentPackage::class) {
    group = "build"
    description = "Signs the generated macOS application and its nested code inside-out."
    dependsOn(embedMacOsHelper, embedMacOsSyncCompanion)
    applicationBundle.set(macOsDistributable)
    developmentEntitlements.set(layout.projectDirectory.file("Config/PosatoDevelopment.entitlements"))
    companionEntitlementsTemplate.set(
        rootProject.layout.projectDirectory.file(
            "macosSyncCompanion/Resources/PosatoMacOSSync.entitlements.template",
        ),
    )
    val companionProfile = providers.gradleProperty("posatoMacOsSyncProvisioningProfile")
    if (companionProfile.isPresent) {
        companionProvisioningProfile.set(file(companionProfile.get()))
    }
    signingIdentity.set(macOsSigningIdentity)
    release.set(false)
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
    release.set(false)
    marketingVersion.set(posatoMarketingVersion)
    buildNumber.set(posatoBuildNumber)
    iconFile.set(layout.projectDirectory.file("Config/Posato.icns"))
    noticeFiles.from(rootProject.files("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"))
}

val macOsReleasePackageRoot = layout.buildDirectory.dir("compose/binaries/main/release-package")
val macOsReleaseApplication = macOsReleasePackageRoot.map { directory -> directory.dir("Posato.app") }
val macOsReleaseDiskImageDirectory = layout.buildDirectory.dir("compose/binaries/main/release-dmg")
val macOsReleaseSigningIdentity = providers.gradleProperty("posatoMacOsReleaseSigningIdentity").orElse("")
val requestedReleaseBuildNumber = providers.gradleProperty("posatoMacOsBuildNumber").orNull

val stageMacOsReleasePackage by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages the embedded macOS application for Developer ID signing."
    dependsOn(embedMacOsHelper, embedMacOsSyncCompanion)
    mustRunAfter(signMacOsDevelopmentPackage, stageMacOsDevelopmentPackage)
    val releaseBuildNumber = requestedReleaseBuildNumber
    doFirst { PosatoVersion.releaseBuildNumber(releaseBuildNumber) }

    from(macOsDistributable)
    into(macOsReleaseApplication)
}

val signMacOsReleasePackage by tasks.registering(SignMacOsDevelopmentPackage::class) {
    group = "distribution"
    description = "Signs the staged macOS application and its nested code with Developer ID and a secure timestamp."
    dependsOn(stageMacOsReleasePackage)
    applicationBundle.set(macOsReleaseApplication)
    developmentEntitlements.set(layout.projectDirectory.file("Config/PosatoDevelopment.entitlements"))
    companionEntitlementsTemplate.set(
        rootProject.layout.projectDirectory.file(
            "macosSyncCompanion/Resources/PosatoMacOSSync.entitlements.template",
        ),
    )
    val companionProfile = providers.gradleProperty("posatoMacOsSyncDeveloperIdProfile")
    if (companionProfile.isPresent) {
        companionProvisioningProfile.set(file(PosatoPaths.expandHome(companionProfile.get())))
    }
    signingIdentity.set(macOsReleaseSigningIdentity)
    release.set(true)
    outputs.upToDateWhen { false }
}

val verifyMacOsReleasePackaging by tasks.registering(VerifyMacOsDevelopmentPackaging::class) {
    group = "verification"
    description = "Verifies the Developer ID signed macOS application, nested code, profile, versions, icon, and runtime."
    dependsOn(signMacOsReleasePackage)
    applicationBundle.set(macOsReleaseApplication)
    signingIdentity.set(macOsReleaseSigningIdentity)
    release.set(true)
    marketingVersion.set(posatoMarketingVersion)
    buildNumber.set(posatoBuildNumber)
    iconFile.set(layout.projectDirectory.file("Config/Posato.icns"))
    noticeFiles.from(rootProject.files("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"))
    runtimeSourceRelease.set(posatoJavaLauncher.map { launcher -> launcher.metadata.installationPath.file("release") })
}

val notarizeMacOsReleaseApplication by tasks.registering(NotarizeMacOsArtifact::class) {
    group = "distribution"
    description = "Notarizes, staples, and assesses the verified Developer ID macOS application."
    dependsOn(verifyMacOsReleasePackaging)
    artifact.set(macOsReleaseApplication.map { directory -> directory.asFile })
    keyId.set(providers.gradleProperty("posatoAscKeyId"))
    issuerId.set(providers.gradleProperty("posatoAscIssuerId"))
    privateKey.set(providers.gradleProperty("posatoAscPrivateKeyPath"))
    releaseSigningIdentity.set(macOsReleaseSigningIdentity)
}

val packageMacOsReleaseDmg = tasks.register<AbstractNativeMacApplicationPackageDmgTask>("packageMacOsReleaseDmg") {
    group = "distribution"
    description = "Packages the notarized macOS application as a DMG."
    dependsOn(notarizeMacOsReleaseApplication)
    packageName.set("Posato")
    packageVersion.set(posatoMarketingVersion)
    destinationDir.set(macOsReleaseDiskImageDirectory)
    appDir.set(macOsReleasePackageRoot)
}

tasks.register<NotarizeMacOsArtifact>("notarizeMacOsRelease") {
    group = "distribution"
    description = "Signs, notarizes, staples, and assesses the macOS release DMG."
    dependsOn(packageMacOsReleaseDmg)
    artifact.set(macOsReleaseDiskImageDirectory.map { directory -> directory.asFile })
    keyId.set(providers.gradleProperty("posatoAscKeyId"))
    issuerId.set(providers.gradleProperty("posatoAscIssuerId"))
    privateKey.set(providers.gradleProperty("posatoAscPrivateKeyPath"))
    releaseSigningIdentity.set(macOsReleaseSigningIdentity)
}

val packageDmg = tasks.register<AbstractNativeMacApplicationPackageDmgTask>("packageDmg") {
    group = "distribution"
    description = "Packages the verified development-signed macOS application as a DMG."
    dependsOn(verifyMacOsDevelopmentPackaging)
    packageName.set("Posato")
    packageVersion.set(posatoMarketingVersion)
    destinationDir.set(layout.buildDirectory.dir("compose/binaries/main/dmg"))
    appDir.set(macOsDevelopmentPackageRoot)
}

tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach {
    dependsOn(packageDmg)
}

tasks.matching { it.name == "runDistributable" }.configureEach {
    dependsOn(verifyMacOsDevelopmentPackaging)
}
