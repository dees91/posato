import app.posato.buildlogic.AppcastExpectation
import app.posato.buildlogic.PosatoPaths
import app.posato.buildlogic.PosatoPublishedFeed
import app.posato.buildlogic.PosatoUpdateFeed
import app.posato.buildlogic.PosatoVersion
import app.posato.buildlogic.ReleaseFloor
import app.posato.buildlogic.UpdateChannel
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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.tasks.AbstractNativeMacApplicationPackageDmgTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

abstract class DownloadVerifiedFile : DefaultTask() {
    @get:Input
    abstract val sourceUrl: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val target = destination.get().asFile
        val downloaded = temporaryDir.resolve(target.name)
        URI(sourceUrl.get()).toURL().openStream().use { input ->
            downloaded.outputStream().use { output -> input.copyTo(output) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(downloaded.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        if (digest != sha256.get()) {
            downloaded.delete()
            throw GradleException("The downloaded ${target.name} does not match its pinned SHA-256.")
        }
        target.parentFile.mkdirs()
        Files.move(downloaded.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

abstract class StageMacOsApplication : DefaultTask() {
    @get:Internal
    abstract val sourceApplication: DirectoryProperty

    @get:Internal
    abstract val stagedApplication: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun stage() {
        val staged = stagedApplication.get().asFile
        staged.deleteRecursively()
        staged.parentFile.mkdirs()
        execOperations.exec {
            commandLine("/usr/bin/ditto", sourceApplication.get().asFile.absolutePath, staged.absolutePath)
        }
    }
}

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

    @get:Input
    abstract val sparkleVersion: Property<String>

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
        val updaterLibrary = applicationCode.resolve("resources/native/libPosatoUpdater.dylib")
        check(updaterLibrary.isFile) { "The packaged updater library is missing." }
        val sparkle = application.resolve("Contents/Frameworks/Sparkle.framework")
        val sparkleVersioned = sparkle.resolve("Versions/B")
        val sparkleAutoupdate = sparkleVersioned.resolve("Autoupdate")
        val sparkleProgress = sparkleVersioned.resolve("Updater.app")
        check(Files.isSymbolicLink(sparkle.resolve("Versions/Current").toPath())) { "The embedded Sparkle framework lost its version links." }
        check(!sparkleVersioned.resolve("XPCServices").exists()) { "The embedded Sparkle framework still carries sandbox XPC services." }
        check(plistValue(sparkleVersioned.resolve("Resources/Info.plist"), "CFBundleShortVersionString") == sparkleVersion.get()) {
            "The embedded Sparkle framework is not the pinned version."
        }
        verifyUpdaterSettings(application.resolve("Contents/Info.plist"))

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
            add(updaterLibrary)
            add(sparkleAutoupdate)
            add(sparkleProgress)
            add(sparkle)
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

    private fun verifyUpdaterSettings(info: File) {
        REQUIRED_UPDATER_SETTINGS.forEach { (key, value) ->
            check(plistValue(info, key) == value) { "The application Info.plist does not set $key to $value." }
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
        val REQUIRED_UPDATER_SETTINGS = mapOf(
            "SURequireSignedFeed" to "true",
            "SUVerifyUpdateBeforeExtraction" to "true",
            "SUSignedFeedFailureExpirationInterval" to "0",
            "SUAutomaticallyUpdate" to "false",
            "SUAllowsAutomaticUpdates" to "false",
            "SUEnableSystemProfiling" to "false",
            "SUEnableAutomaticChecks" to "false",
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
        val updaterLibrary = applicationCode.resolve("resources/native/libPosatoUpdater.dylib")
        check(updaterLibrary.isFile) { "The packaged updater library is missing." }
        removeForeignNativeLibraries(applicationCode)
        signCode(windowLibrary, identity)
        signCode(updaterLibrary, identity)

        if (identity == "-") {
            signSqliteLibrary(applicationCode, identity)
            machOFiles(runtime).forEach { code -> signCode(code, identity) }
            signCode(runtime, identity)
            machOFiles(applicationCode, recursive = false).forEach { code -> signCode(code, identity) }
            signCode(daemon, identity, identifier = "app.posato.macos.proxy-settings")
            signCode(helper, identity, identifier = "app.posato.macos.helper")
            signCompanion(application, identity, entitlements = null)
            signSparkle(application, identity)
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
        signSparkle(application, identity)
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

    private fun signSparkle(
        application: File,
        identity: String,
    ) {
        val sparkle = application.resolve("Contents/Frameworks/Sparkle.framework")
        val versioned = sparkle.resolve("Versions/B")
        check(versioned.isDirectory) { "The embedded Sparkle framework is missing." }
        signCode(versioned.resolve("Autoupdate"), identity)
        signCode(versioned.resolve("Updater.app"), identity)
        signCode(sparkle, identity)
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
            ?: throw GradleException("A macOS release needs a value for $name; see docs/development/apple-provisioning.md.")
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

abstract class GenerateMacOsUpdateFeed : DefaultTask() {
    @get:InputFiles
    abstract val diskImageDirectory: DirectoryProperty

    @get:InputFiles
    abstract val applicationBundle: DirectoryProperty

    @get:InputFile
    @get:Optional
    abstract val releaseNotes: RegularFileProperty

    @get:InputFiles
    abstract val sparkleDistribution: DirectoryProperty

    @get:Input
    abstract val channel: Property<String>

    @get:Input
    abstract val marketingVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val previousBuildNumber: Property<String>

    @get:Input
    @get:Optional
    abstract val candidateDownloadPrefix: Property<String>

    @get:Input
    abstract val keyAccount: Property<String>

    @get:OutputDirectory
    abstract val feedDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val updateChannel = UpdateChannel.entries.firstOrNull { it.propertyValue == channel.get() }
            ?: throw GradleException("Generating an update feed needs -PposatoMacOsUpdateChannel=release or candidate.")
        val diskImage = diskImageDirectory.get().asFile.listFiles().orEmpty().singleOrNull { it.extension == "dmg" }
            ?: throw GradleException("Expected exactly one notarized DMG in ${diskImageDirectory.get().asFile.name}.")
        val notes = releaseNotes.orNull?.asFile
            ?: throw GradleException("An update feed needs -PposatoMacOsReleaseNotes=<plain-text .txt file>.")
        check(notes.extension == "txt") { "Release notes must be a plain-text .txt file." }
        val staged = applicationBundle.get().file("Contents/Info.plist").asFile
        val (feedUrl, publicKey, buildNumber) = publishedApplicationValues(diskImage)
        check(listOf(feedUrl, publicKey, buildNumber) == UPDATE_KEYS.map { key -> plistValue(staged, key) }) {
            "The DMG does not contain the application this release staged."
        }
        val explicitPrevious = previousBuildNumber.orNull?.takeIf(String::isNotBlank)
        val (prefix, previous) = when (updateChannel) {
            UpdateChannel.RELEASE -> {
                check(feedUrl == PosatoUpdateFeed.STABLE_FEED_URL && publicKey == PosatoUpdateFeed.STABLE_PUBLIC_KEY) {
                    "This build does not read the stable feed with the tracked key, so it cannot be released."
                }
                PosatoUpdateFeed.releaseDownloadPrefix(marketingVersion.get()) to releaseFloor(buildNumber, explicitPrevious)
            }

            UpdateChannel.CANDIDATE -> {
                check(!PosatoUpdateFeed.readsStableFeed(feedUrl)) { "A candidate must never read the stable feed." }
                PosatoUpdateFeed.candidateDownloadPrefix(candidateDownloadPrefix.orNull) to explicitPrevious
            }
        }
        val assetName = when (updateChannel) {
            UpdateChannel.RELEASE -> "Posato-${marketingVersion.get()}.dmg"
            UpdateChannel.CANDIDATE -> "Posato-${marketingVersion.get()}-$buildNumber-test.dmg"
        }
        val output = feedDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        val asset = output.resolve(assetName)
        diskImage.copyTo(asset)
        val stagedNotes = output.resolve(asset.nameWithoutExtension + ".txt")
        notes.copyTo(stagedNotes)
        val feed = output.resolve(updateChannel.feedFileName)
        val generator = sparkleDistribution.get().file("bin/generate_appcast").asFile.absolutePath
        val result = execOperations.exec {
            commandLine(
                generator,
                "--account",
                keyAccount.get(),
                "--download-url-prefix",
                prefix,
                "--maximum-deltas",
                "0",
                "--embed-release-notes",
                "-o",
                feed.absolutePath,
                output.absolutePath,
            )
            isIgnoreExitValue = true
        }
        stagedNotes.delete()
        if (result.exitValue != 0 || !feed.isFile) throw GradleException("generate_appcast did not produce ${feed.name}.")
        val archive = asset.readBytes()
        val problems = PosatoUpdateFeed.appcastProblems(
            feed.readBytes(),
            AppcastExpectation(feedUrl, publicKey, buildNumber, previous, prefix + assetName, archive),
        )
        if (problems.isNotEmpty()) {
            throw GradleException("The update feed may not be published:\n" + problems.joinToString("\n"))
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(archive).joinToString("") { "%02x".format(it) }
        output.resolve("SHA256SUMS").writeText("$digest  $assetName\n")
        val leftovers = output.listFiles().orEmpty().map { it.name }.toSet() - setOf(assetName, feed.name, "SHA256SUMS")
        check(leftovers.isEmpty()) { "Unexpected files beside the feed: ${leftovers.joinToString()}." }
    }

    /** The build a release must exceed: the published stable feed's highest build, raised by the explicit property. */
    private fun releaseFloor(
        buildNumber: String,
        explicitPrevious: String?,
    ): String {
        val published = PosatoPublishedFeed.publishedBuildNumber(PosatoUpdateFeed.STABLE_FEED_URL)
        return when (val decision = PosatoPublishedFeed.releaseFloor(buildNumber, published, explicitPrevious)) {
            is ReleaseFloor.Refused -> {
                throw GradleException(decision.reason)
            }

            is ReleaseFloor.Above -> {
                logger.lifecycle("Release build $buildNumber follows the published stable build ${published ?: "(none)"}; floor ${decision.floor}.")
                decision.floor.toString()
            }
        }
    }

    /** The feed URL, key, and build number of the application inside the DMG that will be published. */
    private fun publishedApplicationValues(diskImage: java.io.File): List<String> {
        val mountPoint = temporaryDir.resolve("published")
        mountPoint.deleteRecursively()
        mountPoint.mkdirs()
        val attach = execOperations.exec {
            commandLine(
                "/usr/bin/hdiutil",
                "attach",
                "-readonly",
                "-nobrowse",
                "-noautoopen",
                "-mountpoint",
                mountPoint.absolutePath,
                diskImage.absolutePath,
            )
            standardOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        if (attach.exitValue != 0) throw GradleException("Could not mount ${diskImage.name} to read its application.")
        try {
            val information = mountPoint.resolve("Posato.app/Contents/Info.plist")
            return UPDATE_KEYS.map { key -> plistValue(information, key) }
        } finally {
            val detach = execOperations.exec {
                commandLine("/usr/bin/hdiutil", "detach", mountPoint.absolutePath)
                standardOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
            if (detach.exitValue != 0) {
                execOperations.exec {
                    commandLine("/usr/bin/hdiutil", "detach", "-force", mountPoint.absolutePath)
                    standardOutput = ByteArrayOutputStream()
                    isIgnoreExitValue = true
                }
            }
        }
    }

    private fun plistValue(
        plist: java.io.File,
        key: String,
    ): String {
        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine("/usr/bin/plutil", "-extract", key, "raw", "-o", "-", plist.absolutePath)
            standardOutput = output
            isIgnoreExitValue = true
        }
        val value = output.toString(Charsets.UTF_8).trim()
        if (result.exitValue != 0 || value.isEmpty()) throw GradleException("The application's Info.plist has no $key.")
        return value
    }

    private companion object {
        val UPDATE_KEYS = listOf("SUFeedURL", "SUPublicEDKey", "CFBundleVersion")
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

val pinnedSparkleVersion = "2.10.0"
val sparkleArchive = layout.buildDirectory.file("sparkle/Sparkle-$pinnedSparkleVersion.tar.xz")
val sparkleDistribution = layout.buildDirectory.dir("sparkle/$pinnedSparkleVersion")
val downloadSparkle = tasks.register<DownloadVerifiedFile>("downloadSparkle") {
    group = "build"
    description = "Downloads the pinned Sparkle distribution and verifies its checksum."
    sourceUrl.set("https://github.com/sparkle-project/Sparkle/releases/download/$pinnedSparkleVersion/Sparkle-$pinnedSparkleVersion.tar.xz")
    sha256.set("c2bf58aa8387266ac179357b1415d6f2635f044da8be41042af32425dae6da0c")
    destination.set(sparkleArchive)
}
val extractSparkle = tasks.register<Exec>("extractSparkle") {
    group = "build"
    description = "Extracts the pinned Sparkle framework and release tools."
    inputs.file(downloadSparkle.flatMap { task -> task.destination })
    val distribution = sparkleDistribution.get().asFile
    outputs.dir(distribution)
    doFirst {
        distribution.deleteRecursively()
        distribution.mkdirs()
    }
    commandLine(
        "/usr/bin/tar",
        "-xf",
        sparkleArchive.get().asFile.absolutePath,
        "-C",
        distribution.absolutePath,
        "Sparkle.framework",
        "bin/generate_appcast",
        "bin/generate_keys",
        "bin/sign_update",
        "LICENSE",
    )
}

val updaterResources = layout.buildDirectory.dir("generated/updater")
val updaterLibrary = updaterResources.map { it.file("macos-arm64/native/libPosatoUpdater.dylib") }
val compileUpdater = tasks.register<Exec>("compileUpdater") {
    val source = layout.projectDirectory.file("src/main/objc/Updater.m")
    val javaInstallation = posatoJavaLauncher.get().metadata.installationPath.asFile
    val compiledLibrary = updaterLibrary.get().asFile
    val frameworks = sparkleDistribution.get().asFile
    dependsOn(extractSparkle)
    inputs.file(source)
    inputs.dir(frameworks)
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
        "-F${frameworks.absolutePath}",
        "-framework",
        "Sparkle",
        "-install_name",
        "@loader_path/libPosatoUpdater.dylib",
        "-Wl,-rpath,@loader_path/../../../Frameworks",
        "-I$javaInstallation/include",
        "-I$javaInstallation/include/darwin",
        source.asFile.absolutePath,
        "-o",
        compiledLibrary.absolutePath,
    )
}

val nativeLeafResources = layout.buildDirectory.dir("generated/native-leaves")
val assembleNativeLeaves = tasks.register<Sync>("assembleNativeLeaves") {
    from(compileWindowChrome.map { windowChromeResources.get() })
    from(compileUpdater.map { updaterResources.get() })
    into(nativeLeafResources)
}

tasks.withType<AbstractJPackageTask>().configureEach {
    inputs.dir(assembleNativeLeaves.map { nativeLeafResources.get() })
        .withPropertyName("nativeLeaves")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named<Test>("test") {
    systemProperty("posato.sparkle.version", pinnedSparkleVersion)
}

val updateFeed = PosatoUpdateFeed.resolve(
    providers.gradleProperty("posatoMacOsUpdateChannel").orNull?.takeIf(String::isNotBlank),
    providers.gradleProperty("posatoMacOsUpdateFeedUrl").orNull?.takeIf(String::isNotBlank),
    providers.gradleProperty("posatoMacOsUpdatePublicKey").orNull?.takeIf(String::isNotBlank),
)
val updateFeedUrl = updateFeed.feedUrl
val updatePublicKey = updateFeed.publicKey
val updaterInfoPlistKeys = buildString {
    append("<key>SURequireSignedFeed</key><true/>")
    append("<key>SUVerifyUpdateBeforeExtraction</key><true/>")
    append("<key>SUSignedFeedFailureExpirationInterval</key><integer>0</integer>")
    append("<key>SUAutomaticallyUpdate</key><false/>")
    append("<key>SUAllowsAutomaticUpdates</key><false/>")
    append("<key>SUEnableSystemProfiling</key><false/>")
    append("<key>SUEnableAutomaticChecks</key><false/>")
    if (updateFeedUrl != null && updatePublicKey != null) {
        append("<key>SUFeedURL</key><string>$updateFeedUrl</string>")
        append("<key>SUPublicEDKey</key><string>$updatePublicKey</string>")
        if (updateFeedUrl.startsWith("http://127.0.0.1:")) {
            append("<key>NSAppTransportSecurity</key><dict><key>NSAllowsLocalNetworking</key><true/></dict>")
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.posato.desktop.MainKt"
        javaHome = posatoJavaLauncher.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            appResourcesRootDir.set(assembleNativeLeaves.map { nativeLeafResources.get() })
            packageName = "Posato"
            packageVersion = posatoMarketingVersion
            modules("java.sql")

            macOS {
                bundleID = "app.posato.macos"
                minimumSystemVersion = "15.0"
                packageBuildVersion = posatoBuildNumber
                iconFile.set(layout.projectDirectory.file("Config/Posato.icns"))
                infoPlist {
                    extraKeysRawXml = updaterInfoPlistKeys
                }
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

val embedSparkleFramework by tasks.registering(Exec::class) {
    group = "build"
    description = "Embeds the pinned Sparkle framework without its sandbox XPC services."
    dependsOn(extractSparkle, "createDistributable")
    val source = sparkleDistribution.get().dir("Sparkle.framework").asFile
    val frameworks = macOsDistributable.get().dir("Contents/Frameworks").asFile
    val embedded = frameworks.resolve("Sparkle.framework")
    doFirst {
        embedded.deleteRecursively()
        frameworks.mkdirs()
    }
    commandLine("/usr/bin/ditto", source.absolutePath, embedded.absolutePath)
    doLast {
        embedded.resolve("XPCServices").delete()
        embedded.resolve("Versions/B/XPCServices").deleteRecursively()
    }
}

val signMacOsDevelopmentPackage by tasks.registering(SignMacOsDevelopmentPackage::class) {
    group = "build"
    description = "Signs the generated macOS application and its nested code inside-out."
    dependsOn(embedMacOsHelper, embedMacOsSyncCompanion, embedSparkleFramework)
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

val stageMacOsDevelopmentPackage by tasks.registering(StageMacOsApplication::class) {
    group = "build"
    description = "Stages the verified signed application for macOS packaging."
    dependsOn(verifyMacOsHelperStructure)
    sourceApplication.set(macOsDistributable)
    stagedApplication.set(macOsDevelopmentApplication)
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
    sparkleVersion.set(pinnedSparkleVersion)
}

val macOsReleasePackageRoot = layout.buildDirectory.dir("compose/binaries/main/release-package")
val macOsReleaseApplication = macOsReleasePackageRoot.map { directory -> directory.dir("Posato.app") }
val macOsReleaseDiskImageDirectory = layout.buildDirectory.dir("compose/binaries/main/release-dmg")
val macOsReleaseSigningIdentity = providers.gradleProperty("posatoMacOsReleaseSigningIdentity").orElse("")
val requestedReleaseBuildNumber = providers.gradleProperty("posatoMacOsBuildNumber").orNull
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::isFile)?.inputStream()?.use { input -> load(input) }
}

fun releaseCredential(
    gradleProperty: String,
    environmentVariable: String,
    localKey: String,
): Provider<String> {
    val value = providers.gradleProperty(gradleProperty).orNull?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(environmentVariable).orNull?.takeIf(String::isNotBlank)
        ?: localProperties.getProperty(localKey)?.takeIf(String::isNotBlank)
    return providers.provider { value.orEmpty() }
}

val ascKeyId = releaseCredential("posatoAscKeyId", "POSATO_ASC_KEY_ID", "posato.asc.keyId")
val ascIssuerId = releaseCredential("posatoAscIssuerId", "POSATO_ASC_ISSUER_ID", "posato.asc.issuerId")
val ascPrivateKeyPath = releaseCredential("posatoAscPrivateKeyPath", "POSATO_ASC_PRIVATE_KEY_PATH", "posato.asc.privateKeyPath")

val releaseUpdateChannel = updateFeed.channel?.propertyValue
val checkMacOsUpdateChannel by tasks.registering {
    group = "verification"
    description = "Refuses a Developer ID build without an explicit update channel."
    val channelChosen = releaseUpdateChannel != null
    doLast {
        if (!channelChosen) {
            throw GradleException("A Developer ID build needs -PposatoMacOsUpdateChannel=release, or candidate with a test feed and key.")
        }
    }
}

val stageMacOsReleasePackage by tasks.registering(StageMacOsApplication::class) {
    group = "distribution"
    description = "Stages the embedded macOS application for Developer ID signing."
    dependsOn(checkMacOsUpdateChannel, embedMacOsHelper, embedMacOsSyncCompanion, embedSparkleFramework)
    mustRunAfter(signMacOsDevelopmentPackage, stageMacOsDevelopmentPackage)
    val releaseBuildNumber = requestedReleaseBuildNumber
    inputs.property("posatoReleaseBuildNumber", providers.provider { PosatoVersion.releaseBuildNumber(releaseBuildNumber) })
    sourceApplication.set(macOsDistributable)
    stagedApplication.set(macOsReleaseApplication)
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
    sparkleVersion.set(pinnedSparkleVersion)
    runtimeSourceRelease.set(posatoJavaLauncher.map { launcher -> launcher.metadata.installationPath.file("release") })
}

val notarizeMacOsReleaseApplication by tasks.registering(NotarizeMacOsArtifact::class) {
    group = "distribution"
    description = "Notarizes, staples, and assesses the verified Developer ID macOS application."
    dependsOn(verifyMacOsReleasePackaging)
    artifact.set(macOsReleaseApplication.map { directory -> directory.asFile })
    keyId.set(ascKeyId)
    issuerId.set(ascIssuerId)
    privateKey.set(ascPrivateKeyPath)
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
    keyId.set(ascKeyId)
    issuerId.set(ascIssuerId)
    privateKey.set(ascPrivateKeyPath)
    releaseSigningIdentity.set(macOsReleaseSigningIdentity)
}

tasks.register<GenerateMacOsUpdateFeed>("generateMacOsUpdateFeed") {
    group = "distribution"
    description = "Builds the notarized DMG, then signs its update feed with the Keychain key and validates both together."
    dependsOn("notarizeMacOsRelease", extractSparkle)
    diskImageDirectory.set(macOsReleaseDiskImageDirectory)
    applicationBundle.set(macOsReleaseApplication)
    providers.gradleProperty("posatoMacOsReleaseNotes").orNull?.let { notes -> releaseNotes.set(file(PosatoPaths.expandHome(notes))) }
    sparkleDistribution.set(layout.buildDirectory.dir("sparkle/$pinnedSparkleVersion"))
    channel.set(releaseUpdateChannel.orEmpty())
    marketingVersion.set(posatoMarketingVersion)
    previousBuildNumber.set(providers.gradleProperty("posatoMacOsPreviousBuildNumber"))
    candidateDownloadPrefix.set(providers.gradleProperty("posatoMacOsUpdateDownloadPrefix"))
    keyAccount.set(providers.gradleProperty("posatoMacOsUpdateKeyAccount").orElse("posato-release"))
    feedDirectory.set(layout.buildDirectory.dir("compose/binaries/main/release-feed"))
    outputs.upToDateWhen { false }
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
