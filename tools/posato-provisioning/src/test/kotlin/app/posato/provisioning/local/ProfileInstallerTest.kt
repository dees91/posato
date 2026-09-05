package app.posato.provisioning.local

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.UserPaths
import app.posato.provisioning.model.AppIdentifier
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TEAM = "ABCDE12345"
private val NOW: Instant = Instant.parse("2026-09-05T12:00:00Z")
private const val PROFILE_UUID = "11112222-3333-4444-5555-666677778888"

class ProfileInstallerTest {
    @Test
    fun `installs a valid macOS profile and reports what it wrote`() {
        val home = temporaryHome()
        val paths = UserPaths(home)
        val content = encode("macos bytes")

        val result = installer(home, plist("com.apple.application-identifier", "OSX", AppIdentifier.MACOS_SYNC))
            .install(AppIdentifier.MACOS_SYNC, content, NOW)

        assertTrue(result.changed)
        assertEquals(false, result.installedForXcode)
        assertContentEquals("macos bytes".toByteArray(), paths.posatoDeveloperDirectory.resolve(AppIdentifier.MACOS_SYNC.fileName).readBytes())
        assertEquals(false, paths.xcodeProfilesDirectory.exists())
    }

    @Test
    fun `puts an iOS profile in the Xcode directory under its own identifier`() {
        val home = temporaryHome()
        val paths = UserPaths(home)

        val result = installer(home, plist("application-identifier", "iOS", AppIdentifier.IOS_APPLICATION))
            .install(AppIdentifier.IOS_APPLICATION, encode("ios bytes"), NOW)

        assertTrue(result.installedForXcode)
        assertContentEquals(
            "ios bytes".toByteArray(),
            paths.xcodeProfilesDirectory.resolve("$PROFILE_UUID.mobileprovision").readBytes(),
        )
    }

    @Test
    fun `leaves a working profile untouched when the download does not check out`() {
        // This is the case the whole validate-before-replace shape exists for: a profile for another App ID, another
        // team, or an expired one must not become the file the desktop packaging task signs against.
        val cases = mapOf(
            "another App ID" to plist("com.apple.application-identifier", "OSX", AppIdentifier.MACOS_HELPER),
            "another team" to plist("com.apple.application-identifier", "OSX", AppIdentifier.MACOS_SYNC, team = "ZZZZZ99999"),
            "expired" to plist("com.apple.application-identifier", "OSX", AppIdentifier.MACOS_SYNC, expiry = "2026-01-01T00:00:00Z"),
            "wrong platform" to plist("com.apple.application-identifier", "iOS", AppIdentifier.MACOS_SYNC),
            "not a profile" to "<plist><dict></dict></plist>",
        )

        cases.forEach { (name, decoded) ->
            val home = temporaryHome()
            val existing = seedWorkingProfile(home)

            val failure = assertFailsWith<ProvisioningException>(name) {
                installer(home, decoded).install(AppIdentifier.MACOS_SYNC, encode("replacement"), NOW)
            }

            assertEquals(ErrorCode.PROFILE_INVALID, failure.code, name)
            assertContentEquals("working".toByteArray(), existing.readBytes(), name)
            assertTrue(temporaryFiles(existing.parent).isEmpty(), "$name left a temporary file behind")
        }
    }

    @Test
    fun `writes nothing when the installed bytes already match`() {
        val home = temporaryHome()
        val existing = seedWorkingProfile(home)
        existing.writeBytes("same".toByteArray())

        val result = installer(home, plist("com.apple.application-identifier", "OSX", AppIdentifier.MACOS_SYNC))
            .install(AppIdentifier.MACOS_SYNC, encode("same"), NOW)

        assertEquals(false, result.changed)
        assertContentEquals("same".toByteArray(), existing.readBytes())
        assertTrue(temporaryFiles(existing.parent).isEmpty())
    }

    @Test
    fun `refuses a symbolic link destination rather than writing through it`() {
        val home = temporaryHome()
        val directory = Files.createDirectories(UserPaths(home).posatoDeveloperDirectory)
        val elsewhere = Files.createTempFile("posato-elsewhere", ".provisionprofile")
        elsewhere.writeBytes("untouched".toByteArray())
        Files.createSymbolicLink(directory.resolve(AppIdentifier.MACOS_SYNC.fileName), elsewhere)

        val failure = assertFailsWith<ProvisioningException> {
            installer(home, plist("com.apple.application-identifier", "OSX", AppIdentifier.MACOS_SYNC))
                .install(AppIdentifier.MACOS_SYNC, encode("replacement"), NOW)
        }

        assertEquals(ErrorCode.PROFILE_INSTALL_FAILED, failure.code)
        assertContentEquals("untouched".toByteArray(), elsewhere.readBytes())
    }

    @Test
    fun `fails closed when the profile cannot be decoded at all`() {
        val home = temporaryHome()
        val installer = ProfileInstaller(RecordingRunner.failing("security: unable to decode"), UserPaths(home), TEAM)

        val failure = assertFailsWith<ProvisioningException> {
            installer.install(AppIdentifier.MACOS_SYNC, encode("replacement"), NOW)
        }

        assertEquals(ErrorCode.PROFILE_INVALID, failure.code)
    }

    private fun installer(
        home: Path,
        decoded: String
    ): ProfileInstaller = ProfileInstaller(RecordingRunner.succeeding(decoded), UserPaths(home), TEAM)

    private fun seedWorkingProfile(home: Path): Path {
        val directory = Files.createDirectories(UserPaths(home).posatoDeveloperDirectory)
        return directory.resolve(AppIdentifier.MACOS_SYNC.fileName).also { path -> path.writeBytes("working".toByteArray()) }
    }

    private fun temporaryFiles(directory: Path): List<Path> = directory.listDirectoryEntries().filter { it.fileName.toString().contains(".tmp-") }

    private fun temporaryHome(): Path = Files.createTempDirectory("posato-provisioning-home")

    private fun encode(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun plist(
        identifierKey: String,
        platform: String,
        identifier: AppIdentifier,
        team: String = TEAM,
        expiry: String = "2027-01-01T00:00:00Z",
    ): String =
        """
        <plist><dict>
          <key>UUID</key><string>$PROFILE_UUID</string>
          <key>TeamIdentifier</key><array><string>$team</string></array>
          <key>ExpirationDate</key><date>$expiry</date>
          <key>Platform</key><array><string>$platform</string></array>
          <key>Entitlements</key><dict>
            <key>$identifierKey</key><string>$TEAM.${identifier.bundleId}</string>
          </dict>
        </dict></plist>
        """.trimIndent()
}
