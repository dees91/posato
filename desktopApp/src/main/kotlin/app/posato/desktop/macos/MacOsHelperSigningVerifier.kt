package app.posato.desktop.macos

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

internal object MacOsHelperSigningVerifier {
    private const val APPLICATION_IDENTIFIER = "app.posato.macos"
    private const val HELPER_IDENTIFIER = "app.posato.macos.helper"
    private const val MAXIMUM_OUTPUT_BYTES = 16 * 1024
    private const val COMMAND_TIMEOUT_MILLISECONDS = 5_000L
    private const val MAXIMUM_BUNDLE_PARENT_DEPTH = 16
    private val teamPattern = Regex("[A-Za-z0-9]{10}")

    fun verify(helper: Path): Path {
        val appBundle = applicationBundle()
        val expectedHelper = appBundle.resolve(
            "Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper",
        ).toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(helper))
        check(Files.isRegularFile(helper, LinkOption.NOFOLLOW_LINKS))
        check(Files.isExecutable(helper))
        val resolvedHelper = helper.toRealPath(LinkOption.NOFOLLOW_LINKS)
        check(resolvedHelper == expectedHelper)
        val helperBundle = checkNotNull(resolvedHelper.parent?.parent?.parent)

        runCommand("/usr/bin/codesign", "--verify", "--deep", "--strict", appBundle.toString())
        val applicationSigning = signingDetails(appBundle)
        check(applicationSigning.identifier == APPLICATION_IDENTIFIER)
        check(teamPattern.matches(applicationSigning.teamIdentifier))
        runCommand("/usr/bin/codesign", "--verify", "--strict", helperBundle.toString())
        runCommand("/usr/bin/codesign", "--verify", "--strict", resolvedHelper.toString())
        val helperSigning = signingDetails(helperBundle)
        check(helperSigning.identifier == HELPER_IDENTIFIER)
        check(helperSigning.teamIdentifier == applicationSigning.teamIdentifier)
        return resolvedHelper
    }

    fun installedHelperPath(): Path {
        val appBundle = applicationBundle()
        return appBundle.resolve(
            "Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper",
        ).toAbsolutePath().normalize()
    }

    private fun applicationBundle(): Path {
        val command = ProcessHandle.current().info().command().orElseThrow()
        val executable = Path.of(command).toRealPath()
        return generateSequence(executable.parent) { path -> path.parent }
            .take(MAXIMUM_BUNDLE_PARENT_DEPTH)
            .firstOrNull { candidate -> candidate.name.endsWith(".app") }
            ?: error("Packaged application was not found")
    }

    private fun signingDetails(path: Path): SigningDetails {
        val output = runCommand("/usr/bin/codesign", "-d", "--verbose=4", path.toString())
        val identifier = output.lineSequence()
            .firstOrNull { line -> line.startsWith("Identifier=") }
            ?.substringAfter('=')
            ?: error("Signing identifier is absent")
        val teamIdentifier = output.lineSequence()
            .firstOrNull { line -> line.startsWith("TeamIdentifier=") }
            ?.substringAfter('=')
            ?: error("Signing team is absent")
        return SigningDetails(identifier, teamIdentifier)
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
    )
}
