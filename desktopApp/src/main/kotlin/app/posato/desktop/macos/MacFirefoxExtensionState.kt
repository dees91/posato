package app.posato.desktop.macos

import app.posato.feature.onboarding.MacFirefoxDetection
import app.posato.feature.onboarding.MacFirefoxExtension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

internal class MacFirefoxExtensionState(
    private val ioDispatcher: CoroutineDispatcher,
    private val applicationRoots: List<Path> = defaultApplicationRoots(),
    private val extensionPackage: Path? = bundledFirefoxExtension(),
    private val launch: (List<String>) -> Unit = { command -> ProcessBuilder(command).start() },
    private val fetchStatus: () -> String? = ::fetchFirefoxExtensionStatus,
) : MacFirefoxExtension {
    override suspend fun detect(): MacFirefoxDetection = withContext(ioDispatcher) {
        val found = findFirefox()
        MacFirefoxDetection(installed = found != null, applicationName = found?.first?.removeSuffix(APP_SUFFIX))
    }

    override suspend fun install(): Boolean = withContext(ioDispatcher) {
        val target = findFirefox()?.second
        val packageFile = extensionPackage?.takeIf { Files.isRegularFile(it) }
        if (target == null || packageFile == null) {
            return@withContext false
        }
        runCatching { launch(listOf(OPEN_EXECUTABLE, "-a", target.toString(), packageFile.toString())) }.isSuccess
    }

    override suspend fun verified(): Boolean = withContext(ioDispatcher) {
        firefoxExtensionSeen(fetchStatus())
    }

    private fun findFirefox(): Pair<String, Path>? {
        applicationRoots.forEach { root ->
            FIREFOX_APPLICATION_NAMES.forEach { name ->
                val candidate = root.resolve(name)
                if (Files.isDirectory(candidate)) {
                    return name to candidate
                }
            }
        }
        return null
    }

    companion object {
        val FIREFOX_APPLICATION_NAMES: List<String> = listOf(
            "Firefox.app",
            "Firefox Nightly.app",
            "Firefox Developer Edition.app",
        )
        const val FIREFOX_LOOPBACK_PORT: Int = 48_151
        private const val APP_SUFFIX = ".app"
        private const val OPEN_EXECUTABLE = "/usr/bin/open"
        private const val STATUS_PATH = "/firefox-extension-status"
        private const val STATUS_TIMEOUT_MILLISECONDS = 1_000
        private const val MAXIMUM_STATUS_BYTES = 64
        private const val EXTENSION_RESOURCE_PATH = "Contents/Resources/PosatoFirefoxExtension.xpi"
        private const val MAXIMUM_BUNDLE_PARENT_DEPTH = 16

        fun defaultApplicationRoots(): List<Path> {
            return listOf(Path.of("/Applications"), Path.of(System.getProperty("user.home"), "Applications"))
        }

        fun bundledFirefoxExtension(): Path? {
            return try {
                val command = ProcessHandle.current().info().command().orElseThrow()
                val executable = Path.of(command).toRealPath()
                generateSequence(executable.parent) { path -> path.parent }
                    .take(MAXIMUM_BUNDLE_PARENT_DEPTH)
                    .firstOrNull { candidate -> candidate.fileName.toString().endsWith(APP_SUFFIX) }
                    ?.resolve(EXTENSION_RESOURCE_PATH)
            } catch (_: Exception) {
                null
            }
        }

        fun fetchFirefoxExtensionStatus(): String? {
            return try {
                val connection = URI("http://127.0.0.1:$FIREFOX_LOOPBACK_PORT$STATUS_PATH").toURL()
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = STATUS_TIMEOUT_MILLISECONDS
                connection.readTimeout = STATUS_TIMEOUT_MILLISECONDS
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return null
                }
                connection.inputStream.use { input -> input.readNBytes(MAXIMUM_STATUS_BYTES).decodeToString() }
            } catch (_: Exception) {
                null
            }
        }
    }
}

internal fun firefoxExtensionSeen(body: String?): Boolean {
    if (body == null) {
        return false
    }
    val prefix = "seen:"
    return body.startsWith(prefix) && body.length > prefix.length && body.drop(prefix.length).all { it.isDigit() }
}
