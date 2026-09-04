package app.posato.feature.sync.macos

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MacOsSyncCompanionVerifierTest {
    @Test
    fun `given a missing nested companion when verified then verification fails`() {
        val root = Files.createTempDirectory("posato-missing-companion")
        Files.createDirectories(root.resolve("Contents/Helpers"))

        assertFails {
            MacOsSyncCompanionVerifier().verify(root)
        }
    }

    @Test
    fun `given a signed ad-hoc application when verified then the companion executable is returned`() {
        val root = signedApplication()

        val executable = MacOsSyncCompanionVerifier().verify(root)

        assertTrue(executable.fileName.toString() == MacOsSyncCompanionProtocol.COMPANION_EXECUTABLE)
    }

    @Test
    fun `given a wrong companion identifier when verified then verification fails`() {
        val root = signedApplication(companionIdentifier = "app.posato.macos.sync.wrong")

        assertFails {
            MacOsSyncCompanionVerifier().verify(root)
        }
    }

    @Test
    fun `given a wrong application identifier when verified then verification fails`() {
        val root = signedApplication(applicationIdentifier = "app.posato.macos.wrong")

        assertFails {
            MacOsSyncCompanionVerifier().verify(root)
        }
    }

    @Test
    fun `given a companion re-signed with sandbox entitlements when verified then verification fails`() {
        val root = signedApplication()
        val companion = root.resolve("Contents/Helpers/PosatoMacOSSync.app")
        val entitlements = Files.createTempFile("posato-wrong-entitlements", ".plist")
        Files.writeString(entitlements, SANDBOX_ENTITLEMENTS)
        sign(
            companion.resolve("Contents/MacOS/${MacOsSyncCompanionProtocol.COMPANION_EXECUTABLE}"),
            MacOsSyncCompanionProtocol.COMPANION_IDENTIFIER,
            entitlements,
        )
        sign(companion, MacOsSyncCompanionProtocol.COMPANION_IDENTIFIER, entitlements)

        assertFails {
            MacOsSyncCompanionVerifier().verify(root)
        }
    }

    @Test
    fun `given the staged ad-hoc application when verified then the companion executable is returned`() {
        val root = Path.of(
            "../desktopApp/build/compose/binaries/main/development-package/Posato.app",
        )
        if (!Files.isDirectory(root)) {
            return
        }

        val executable = MacOsSyncCompanionVerifier().verify(root)
        checkNotNull(MacOsSyncCompanionClient.verified(root))
        assertTrue(executable.fileName.toString() == MacOsSyncCompanionProtocol.COMPANION_EXECUTABLE)
    }

    private fun signedApplication(
        applicationIdentifier: String = MacOsSyncCompanionProtocol.APPLICATION_IDENTIFIER,
        companionIdentifier: String = MacOsSyncCompanionProtocol.COMPANION_IDENTIFIER,
    ): Path {
        val root = Files.createTempDirectory("posato-companion-verify").resolve("Posato.app")
        val companion = root.resolve("Contents/Helpers/PosatoMacOSSync.app")
        val applicationExecutable = root.resolve("Contents/MacOS/Posato")
        val companionExecutable = companion.resolve(
            "Contents/MacOS/${MacOsSyncCompanionProtocol.COMPANION_EXECUTABLE}",
        )
        Files.createDirectories(applicationExecutable.parent)
        Files.createDirectories(companionExecutable.parent)
        writeInfoPlist(root, identifier = applicationIdentifier, executable = "Posato")
        writeInfoPlist(
            companion,
            identifier = companionIdentifier,
            executable = MacOsSyncCompanionProtocol.COMPANION_EXECUTABLE,
        )
        compileStub(applicationExecutable)
        compileStub(companionExecutable)
        sign(companionExecutable, companionIdentifier)
        sign(companion, companionIdentifier)
        sign(applicationExecutable, applicationIdentifier)
        sign(root, applicationIdentifier)
        return root
    }

    private fun writeInfoPlist(
        bundle: Path,
        identifier: String,
        executable: String,
    ) {
        val plist = bundle.resolve("Contents/Info.plist")
        Files.createDirectories(plist.parent)
        Files.writeString(
            plist,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleExecutable</key>
                <string>$executable</string>
                <key>CFBundleIdentifier</key>
                <string>$identifier</string>
                <key>CFBundlePackageType</key>
                <string>APPL</string>
            </dict>
            </plist>
            """.trimIndent(),
        )
    }

    private fun compileStub(path: Path) {
        val source = Files.createTempFile("posato-stub", ".c")
        Files.writeString(source, "int main(void) { return 0; }\n")
        try {
            runCommand("/usr/bin/cc", "-o", path.toString(), source.toString())
        } finally {
            Files.deleteIfExists(source)
        }
    }

    private fun sign(
        path: Path,
        identifier: String,
        entitlements: Path? = null,
    ) {
        val arguments = mutableListOf(
            "/usr/bin/codesign",
            "--force",
            "--sign",
            "-",
            "--identifier",
            identifier,
        )
        if (entitlements != null) {
            arguments += listOf("--entitlements", entitlements.toString())
        }
        arguments += path.toString()
        runCommand(*arguments.toTypedArray())
    }

    private fun runCommand(vararg arguments: String) {
        val process = ProcessBuilder(*arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readNBytes(MAXIMUM_OUTPUT_BYTES)
        check(process.waitFor() == 0) { output.toString(Charsets.UTF_8) }
    }

    private companion object {
        const val MAXIMUM_OUTPUT_BYTES: Int = 16 * 1024
        val SANDBOX_ENTITLEMENTS: String =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>com.apple.security.app-sandbox</key>
                <true/>
            </dict>
            </plist>
            """.trimIndent()
    }
}
