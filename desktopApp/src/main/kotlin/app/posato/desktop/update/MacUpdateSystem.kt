package app.posato.desktop.update

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

internal class MacUpdateSystem(
    private val bundle: Path,
) {
    private val startupBuild: String? by lazy { readBundleVersion() }
    private val runningTeam: String? by lazy { readSigningTeam() }

    val runningBuild: String
        get() {
            return startupBuild.orEmpty()
        }

    fun warmUp() {
        startupBuild
        runningTeam
    }

    fun installer(): InstallerObservation {
        val uid = runCommand(ID_COMMAND, "-u")?.takeIf { result -> result.exitCode == 0 }?.output?.trim()
        if (uid == null || !uid.all(Char::isDigit)) {
            return InstallerObservation.UNKNOWN
        }
        val domains = listOf("gui/$uid/", "user/$uid/", "system/").map { domain ->
            val printed = runCommand(LAUNCHCTL_COMMAND, "print", domain + SPARKLE_INSTALLER_LABEL)
            if (printed == null) {
                LaunchctlJobState.UNKNOWN
            } else {
                parseLaunchctlPrint(SPARKLE_INSTALLER_LABEL, printed.exitCode, printed.output)
            }
        }
        return combineInstallerObservations(domains, autoupdateRunning())
    }

    fun bundleIdentity(): BundleIdentity {
        return BundleIdentity(
            runningBuild = runningBuild,
            onDiskBuild = readBundleVersion(),
            signedByTeam = runningTeam != null && readSigningTeam() == runningTeam,
        )
    }

    fun storedProxies(): StoredProxyEvidence {
        val converted = runCommand(PLUTIL_COMMAND, "-convert", "xml1", "-o", "-", SYSTEM_CONFIGURATION_PREFERENCES)
        if (converted == null || converted.exitCode != 0) {
            return StoredProxyEvidence.UNREADABLE
        }
        return evaluateStoredProxies(converted.output)
    }

    private fun autoupdateRunning(): Boolean? {
        val autoupdate = bundle.resolve(AUTOUPDATE_RELATIVE_PATH).toString()
        return try {
            ProcessHandle.allProcesses().use { processes ->
                processes.anyMatch { process -> process.info().command().orElse(null) == autoupdate }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private fun readBundleVersion(): String? {
        val plist = bundle.resolve(INFO_PLIST_RELATIVE_PATH).toString()
        val extracted = runCommand(PLUTIL_COMMAND, "-extract", "CFBundleVersion", "raw", "-o", "-", plist) ?: return null
        return extracted.output.trim().takeIf { extracted.exitCode == 0 && it.isNotEmpty() }
    }

    private fun readSigningTeam(): String? {
        val verified = runCommand(CODESIGN_COMMAND, "--verify", "--deep", "--strict", bundle.toString())
        if (verified == null || verified.exitCode != 0) {
            return null
        }
        val details = runCommand(CODESIGN_COMMAND, "-d", "--verbose=4", bundle.toString())
        if (details == null || details.exitCode != 0) {
            return null
        }
        val lines = details.output.lines()
        val identifier = lines.firstOrNull { line -> line.startsWith("Identifier=") }?.substringAfter('=')
        val team = lines.firstOrNull { line -> line.startsWith("TeamIdentifier=") }?.substringAfter('=')
        return team?.takeIf { identifier == APPLICATION_IDENTIFIER && TEAM_PATTERN.matches(it) }
    }

    companion object {
        fun forRunningApplication(): MacUpdateSystem? {
            val command = ProcessHandle.current().info().command().orElse(null) ?: return null
            val executable = Path.of(command).toRealPath()
            val bundle = generateSequence(executable.parent) { path -> path.parent }
                .take(MAXIMUM_BUNDLE_PARENT_DEPTH)
                .firstOrNull { candidate -> candidate.name.endsWith(".app") }
                ?: return null
            return MacUpdateSystem(bundle)
        }
    }
}

private class CommandResult(
    val exitCode: Int,
    val output: String,
)

private fun runCommand(vararg arguments: String): CommandResult? {
    val process = try {
        ProcessBuilder(arguments.toList())
            .apply {
                environment().clear()
                redirectErrorStream(true)
            }.start()
    } catch (_: IOException) {
        return null
    }
    val output = CompletableFuture.supplyAsync { process.inputStream.readNBytes(MAXIMUM_OUTPUT_BYTES + 1) }
    return try {
        if (!process.waitFor(COMMAND_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            null
        } else {
            val bytes = output.get(COMMAND_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
            if (bytes.size > MAXIMUM_OUTPUT_BYTES) null else CommandResult(process.exitValue(), bytes.toString(Charsets.UTF_8))
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        process.destroyForcibly()
        null
    } catch (_: Exception) {
        process.destroyForcibly()
        null
    }
}

internal const val PINNED_SPARKLE_VERSION: String = "2.10.0"
internal const val SPARKLE_INSTALLER_LABEL: String = "app.posato.macos-sparkle-updater"

private const val APPLICATION_IDENTIFIER: String = "app.posato.macos"
private const val AUTOUPDATE_RELATIVE_PATH: String = "Contents/Frameworks/Sparkle.framework/Versions/B/Autoupdate"
private const val INFO_PLIST_RELATIVE_PATH: String = "Contents/Info.plist"
private const val SYSTEM_CONFIGURATION_PREFERENCES: String = "/Library/Preferences/SystemConfiguration/preferences.plist"
private const val ID_COMMAND: String = "/usr/bin/id"
private const val LAUNCHCTL_COMMAND: String = "/bin/launchctl"
private const val PLUTIL_COMMAND: String = "/usr/bin/plutil"
private const val CODESIGN_COMMAND: String = "/usr/bin/codesign"
private const val MAXIMUM_OUTPUT_BYTES: Int = 1024 * 1024
private const val COMMAND_TIMEOUT_MILLISECONDS: Long = 10_000L
private const val MAXIMUM_BUNDLE_PARENT_DEPTH: Int = 16
private val TEAM_PATTERN = Regex("[A-Za-z0-9]{10}")
