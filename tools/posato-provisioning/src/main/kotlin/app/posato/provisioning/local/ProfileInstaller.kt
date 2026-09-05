package app.posato.provisioning.local

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Subprocess
import app.posato.provisioning.core.UserPaths
import app.posato.provisioning.model.AppIdentifier
import app.posato.provisioning.model.ApplePlatform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.Base64
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

private const val OWNER_ONLY_FILE = "rw-------"
private const val TEAM_PREFIX_LENGTH = 10

data class InstallResult(
    val changed: Boolean,
    val installedForXcode: Boolean,
)

/**
 * Writes a downloaded profile where the build and Xcode look for it, but only after it checks out.
 *
 * The bytes are validated as a decoded profile before they replace anything, and the replacement is atomic, so a
 * failed or interrupted run cannot leave a half-written profile in the place a working one used to be. An existing
 * symbolic link is refused rather than followed, because following one would write through to a path the maintainer
 * never named.
 */
class ProfileInstaller(
    private val subprocess: Subprocess,
    private val userPaths: UserPaths,
    private val team: String,
) {
    fun install(
        identifier: AppIdentifier,
        profileContent: String,
        now: Instant
    ): InstallResult {
        val bytes = decodeContent(profileContent)
        val directory = Files.createDirectories(userPaths.posatoDeveloperDirectory)
        val destination = directory.resolve(identifier.fileName)
        refuseSymbolicLink(destination)
        val temporary = directory.resolve("${identifier.fileName}.tmp-${System.nanoTime()}")
        val decoded = try {
            Files.write(temporary, bytes)
            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString(OWNER_ONLY_FILE))
            validate(identifier, temporary, now)
        } catch (failure: ProvisioningException) {
            temporary.deleteIfExists()
            throw failure
        }
        val changed = !destination.exists() || !Files.readAllBytes(destination).contentEquals(bytes)
        if (changed) {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            temporary.deleteIfExists()
        }
        return InstallResult(changed, installForXcode(identifier, decoded, bytes))
    }

    /**
     * Xcode reads iOS profiles from its own directory, named by the profile's own identifier.
     *
     * macOS profiles are not copied there: nothing reads them from that directory, and the desktop packaging task
     * is given an absolute path instead.
     */
    private fun installForXcode(
        identifier: AppIdentifier,
        decoded: DecodedProfile,
        bytes: ByteArray
    ): Boolean {
        if (identifier.platform != ApplePlatform.IOS) return false
        val uuid = decoded.uuid ?: return false
        val directory = Files.createDirectories(userPaths.xcodeProfilesDirectory)
        val destination = directory.resolve("$uuid.mobileprovision")
        refuseSymbolicLink(destination)
        if (destination.exists() && Files.readAllBytes(destination).contentEquals(bytes)) return false
        val temporary = directory.resolve("$uuid.mobileprovision.tmp-${System.nanoTime()}")
        Files.write(temporary, bytes)
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString(OWNER_ONLY_FILE))
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    private fun validate(
        identifier: AppIdentifier,
        file: Path,
        now: Instant
    ): DecodedProfile {
        val output = subprocess.run(listOf("/usr/bin/security", "cms", "-D", "-i", file.toString()))
        output.requireSuccess(ErrorCode.PROFILE_INVALID, "Decoding the downloaded profile", DECODE_HINT)
        val decoded = DecodedProfile.decode(output.stdout) ?: throw invalid("it could not be decoded as a provisioning profile")
        reject(identifier, decoded, now)?.let { reason -> throw invalid(reason) }
        return decoded
    }

    /** Every reason a downloaded profile is not the profile that was asked for, or nothing when it is. */
    private fun reject(
        identifier: AppIdentifier,
        decoded: DecodedProfile,
        now: Instant
    ): String? {
        val applicationIdentifier = decoded.applicationIdentifier ?: return "it carries no application identifier"
        val expectedPlatform = if (identifier.platform == ApplePlatform.IOS) "iOS" else "OSX"
        return when {
            !applicationIdentifier.endsWith(".${identifier.bundleId}") -> {
                "it is not for ${identifier.bundleId}"
            }

            applicationIdentifier.removeSuffix(".${identifier.bundleId}").length != TEAM_PREFIX_LENGTH -> {
                "its application identifier carries no team prefix"
            }

            decoded.teamIdentifier != team -> {
                "it belongs to a different Apple development team"
            }

            decoded.expiresAt == null || !decoded.expiresAt.isAfter(now) -> {
                "it has already expired"
            }

            decoded.platforms.isNotEmpty() && expectedPlatform !in decoded.platforms -> {
                "it is not a $expectedPlatform profile"
            }

            else -> {
                null
            }
        }
    }

    private fun decodeContent(profileContent: String): ByteArray = try {
        Base64.getMimeDecoder().decode(profileContent)
    } catch (exception: IllegalArgumentException) {
        throw ProvisioningException(
            ErrorCode.PROFILE_INVALID,
            "App Store Connect returned a profile this tool could not decode.",
            "Rerun the command; if it repeats, recreate the profile with --replace.",
            exception,
        )
    }

    private fun refuseSymbolicLink(path: Path) {
        if (Files.isSymbolicLink(path)) {
            throw ProvisioningException(
                ErrorCode.PROFILE_INSTALL_FAILED,
                "The profile destination is a symbolic link, which this tool refuses to write through.",
                "Remove the link and rerun the command.",
            )
        }
    }

    private fun invalid(reason: String): ProvisioningException = ProvisioningException(
        ErrorCode.PROFILE_INVALID,
        "The downloaded profile was not installed because $reason.",
        "Rerun with --replace to have App Store Connect issue a new profile.",
    )

    private companion object {
        const val DECODE_HINT = "Confirm /usr/bin/security is available, then rerun the command."
    }
}
