package app.posato.feature.sync.macos

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

internal class MacOsSyncCompanionVerifier {
    fun verify(applicationRoot: Path): Path {
        val expected = applicationRoot.resolve(MacOsSyncCompanionProtocol.RELATIVE_EXECUTABLE)
            .toAbsolutePath()
            .normalize()
        check(!Files.isSymbolicLink(expected))
        check(Files.isRegularFile(expected, LinkOption.NOFOLLOW_LINKS))
        check(Files.isExecutable(expected))
        val resolved = expected.toRealPath(LinkOption.NOFOLLOW_LINKS)
        check(resolved == expected)
        val companionBundle = checkNotNull(resolved.parent?.parent?.parent)
        check(companionBundle.name == "PosatoMacOSSync.app")
        runCommand("/usr/bin/codesign", "--verify", "--deep", "--strict", applicationRoot.toString())
        runCommand("/usr/bin/codesign", "--verify", "--strict", companionBundle.toString())
        runCommand("/usr/bin/codesign", "--verify", "--strict", resolved.toString())
        val applicationSigning = signingDetails(applicationRoot)
        val companionSigning = signingDetails(companionBundle)
        check(applicationSigning.identifier == MacOsSyncCompanionProtocol.APPLICATION_IDENTIFIER)
        check(companionSigning.identifier == MacOsSyncCompanionProtocol.COMPANION_IDENTIFIER)
        if (applicationSigning.isAdHoc || companionSigning.isAdHoc) {
            check(applicationSigning.isAdHoc && companionSigning.isAdHoc)
        } else {
            check(TEAM_PATTERN.matches(applicationSigning.teamIdentifier))
            check(companionSigning.teamIdentifier == applicationSigning.teamIdentifier)
        }
        return resolved
    }

    private fun signingDetails(path: Path): SigningDetails {
        val output = runCommand("/usr/bin/codesign", "-d", "--verbose=4", path.toString())
        val lines = output.lineSequence().map(String::trim).toList()
        val identifier = lines.firstOrNull { line -> line.startsWith("Identifier=") }
            ?.substringAfter('=')
            ?: error("Signing identifier is absent")
        val teamIdentifier = lines.firstOrNull { line -> line.startsWith("TeamIdentifier=") }
            ?.substringAfter('=')
            ?: "not set"
        val isAdHoc = lines.any { line -> line == "Signature=adhoc" } || teamIdentifier == "not set"
        return SigningDetails(identifier, teamIdentifier, isAdHoc)
    }

    private fun runCommand(vararg arguments: String): String {
        val process = try {
            ProcessBuilder(arguments.toList())
                .apply {
                    environment().clear()
                    redirectErrorStream(true)
                }.start()
        } catch (_: IOException) {
            error("Signing verification is unavailable")
        }
        val finished = try {
            process.waitFor(COMMAND_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            error("Signing verification was cancelled")
        }
        if (!finished) {
            process.destroyForcibly()
            error("Signing verification timed out")
        }
        val output = process.inputStream.readNBytes(MAXIMUM_OUTPUT_BYTES + 1)
        check(output.size <= MAXIMUM_OUTPUT_BYTES)
        check(process.exitValue() == 0)
        return output.toString(Charsets.UTF_8)
    }

    private data class SigningDetails(
        val identifier: String,
        val teamIdentifier: String,
        val isAdHoc: Boolean,
    )

    private companion object {
        const val MAXIMUM_OUTPUT_BYTES: Int = 16 * 1024
        const val COMMAND_TIMEOUT_MILLISECONDS: Long = 5_000L
        val TEAM_PATTERN = Regex("[A-Za-z0-9]{10}")
    }
}
