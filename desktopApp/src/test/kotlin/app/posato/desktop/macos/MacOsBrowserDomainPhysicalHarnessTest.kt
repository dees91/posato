package app.posato.desktop.macos

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Maintainer-gated physical harness for the MACOS-004 checklist. It runs only when the
 * `POSATO_MACOS_004_PHYSICAL` environment variable is set, drives the installed
 * development-signed package, and needs the maintainer at the Mac: Apply raises the
 * administrator authentication dialog and the browser rows are performed while the harness
 * holds enforcement active. The gate is not a Gradle input, so run the task with `--rerun`.
 *
 * The helper accepts only a parent process signed as the application, so the harness compiles a
 * minimal parent process and signs it with the local development identity
 * (`posato.macos.signingIdentity` in `local.properties` or `POSATO_MACOS_SIGNING_IDENTITY`).
 * Marker files under `build/verification/macos-004` drive the run: `ENABLE_APPROVED` after the
 * System Settings approval, `APPLY_GO` when the maintainer is ready to authenticate, `ROWS_DONE`
 * after the manual rows, and `ABORT` to stop early with restoration.
 */
class MacOsBrowserDomainPhysicalHarnessTest {
    @Test
    fun `given the physical gate when set then enforcement holds active for manual rows`() {
        if (!gateIsSet()) {
            return
        }
        val run = PhysicalRun(evidenceDirectory())
        run.reset()
        val helperPath = Path.of(System.getenv(HELPER_ENV) ?: DEFAULT_HELPER_PATH)
        assertTrue(Files.isExecutable(helperPath), "Installed helper is missing: $helperPath")
        val parentProcess = signedParentProcess(run.directory)
        run.record("parent process compiled and signed")
        val baseline = proxyBaseline()
        run.record("baseline web=<$baseline>")
        MacOsHelperClient(helperPath = helperPath, launchPrefix = listOf(parentProcess.toString())).use { client ->
            holdActive(client, run)
        }
        val restored = proxyBaseline()
        run.record("restored web=<$restored>")
        assertEquals(baseline, restored, "Proxy baseline was not restored byte-identical")
    }

    private fun holdActive(
        client: MacOsHelperClient,
        run: PhysicalRun,
    ) {
        val enabled = waitForEnable(client, run)
        assertEquals(HelperResult.Outcome.Success, enabled.outcome, "Helper was not enabled")
        run.record("waiting for APPLY_GO: create it, then authenticate at the administrator prompt")
        run.waitFor(run.applyGo, APPLY_GO_TIMEOUT_MILLISECONDS)
        val enforcer = MacOsBrowserDomainEnforcer(client)
        val started = enforcer.start(listOf(SELECTED_DOMAIN))
        if (started is BrowserDomainEnforcementResult.Failed) {
            run.record("start failed ${started.result.categories()}")
        }
        val active = assertIs<BrowserDomainEnforcementResult.Active>(started, "Enforcement did not activate")
        run.record("active port=${active.port}")
        Files.writeString(run.activeMarker, "${active.port}\n")
        try {
            run.waitFor(run.rowsDone, HOLD_TIMEOUT_MILLISECONDS)
            run.record("manual rows done")
        } finally {
            Files.deleteIfExists(run.activeMarker)
            val cleared = enforcer.clear()
            run.record("cleared ${cleared.categories()}")
            assertEquals(HelperResult.Outcome.Success, cleared.outcome, "Enforcement was not cleared")
        }
    }

    private fun waitForEnable(
        client: MacOsHelperClient,
        run: PhysicalRun,
    ): HelperResult {
        var result = client.enable()
        run.record("enable ${result.categories()}")
        if (result.outcome != HelperResult.Outcome.Success) {
            run.record("approve the helper under System Settings > General > Login Items, then create ENABLE_APPROVED")
            run.waitFor(run.enableApproved, ENABLE_TIMEOUT_MILLISECONDS)
            result = client.enable()
            run.record("enable retry ${result.categories()}")
        }
        return result
    }

    private fun signedParentProcess(directory: Path): Path {
        val identity = System.getenv(SIGNING_IDENTITY_ENV)?.takeIf { value -> value.isNotBlank() }
            ?: localProperty(SIGNING_IDENTITY_PROPERTY)
            ?: error("Signing identity is absent: set $SIGNING_IDENTITY_ENV or $SIGNING_IDENTITY_PROPERTY")
        val source = directory.resolve("helper-parent.c")
        val binary = directory.resolve("helper-parent")
        Files.writeString(source, helperParentSource)
        runTool("/usr/bin/cc", "-Wall", "-Werror", "-o", binary.toString(), source.toString())
        runTool("/usr/bin/codesign", "--force", "--sign", identity, "--identifier", APPLICATION_IDENTIFIER, binary.toString())
        return binary
    }

    private fun localProperty(key: String): String? {
        val file = repositoryRoot().resolve("local.properties")
        if (!Files.isRegularFile(file)) {
            return null
        }
        return Files.readAllLines(file)
            .firstOrNull { line -> line.startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
    }

    private fun runTool(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.readAllBytes().decodeToString()
        check(process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "${command.first()} timed out" }
        check(process.exitValue() == 0) { "${command.first()} failed: $output" }
    }

    private fun gateIsSet(): Boolean {
        return System.getenv(GATE)?.isNotBlank() == true
    }

    private fun evidenceDirectory(): Path {
        return Files.createDirectories(repositoryRoot().resolve("build/verification/macos-004"))
    }

    private fun repositoryRoot(): Path {
        return generateSequence(Path.of("").toAbsolutePath()) { path -> path.parent }
            .firstOrNull { candidate -> Files.isRegularFile(candidate.resolve("settings.gradle.kts")) }
            ?: error("Repository root was not found")
    }

    private fun HelperResult.categories(): String {
        return "outcome=$outcome state=$serviceState phase=$ownershipPhase action=$requiredAction failure=$failure"
    }

    private fun proxyBaseline(): String {
        return readProxy("Wi-Fi", "web") + "|" + readProxy("Wi-Fi", "secureweb")
    }

    private fun readProxy(
        service: String,
        kind: String,
    ): String {
        return try {
            val process = ProcessBuilder("networksetup", "-get${kind}proxy", service)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readAllBytes().decodeToString()
            process.waitFor()
            output.trim().replace(Regex("\\s+"), " ")
        } catch (_: Exception) {
            "unavailable"
        }
    }

    private class PhysicalRun(
        val directory: Path,
    ) {
        val runLog: Path = directory.resolve("run.md")
        val activeMarker: Path = directory.resolve("ACTIVE")
        val abortMarker: Path = directory.resolve("ABORT")
        val enableApproved: Path = directory.resolve("ENABLE_APPROVED")
        val applyGo: Path = directory.resolve("APPLY_GO")
        val rowsDone: Path = directory.resolve("ROWS_DONE")

        fun reset() {
            listOf(activeMarker, abortMarker, enableApproved, applyGo, rowsDone).forEach { marker ->
                Files.deleteIfExists(marker)
            }
            if (!Files.isRegularFile(runLog)) {
                Files.writeString(runLog, "# MACOS-004 physical run\n")
            }
        }

        fun record(line: String) {
            Files.writeString(
                runLog,
                "${Instant.now()} $line\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }

        fun waitFor(
            marker: Path,
            timeoutMilliseconds: Long,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMilliseconds
            while (!Files.isRegularFile(marker)) {
                check(!Files.isRegularFile(abortMarker)) { "Aborted by the maintainer while waiting for ${marker.fileName}" }
                check(System.currentTimeMillis() <= deadline) { "Timed out waiting for ${marker.fileName}" }
                Thread.sleep(POLL_MILLISECONDS)
            }
            record("observed ${marker.fileName}")
        }
    }

    private companion object {
        const val GATE = "POSATO_MACOS_004_PHYSICAL"
        const val HELPER_ENV = "POSATO_MACOS_004_HELPER"
        const val SIGNING_IDENTITY_ENV = "POSATO_MACOS_SIGNING_IDENTITY"
        const val SIGNING_IDENTITY_PROPERTY = "posato.macos.signingIdentity"
        const val APPLICATION_IDENTIFIER = "app.posato.macos"
        const val DEFAULT_HELPER_PATH = "/Applications/Posato-MACOS-004.app/Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper"
        const val SELECTED_DOMAIN = "example.com"
        const val ENABLE_TIMEOUT_MILLISECONDS = 10 * 60 * 1_000L
        const val APPLY_GO_TIMEOUT_MILLISECONDS = 15 * 60 * 1_000L
        const val HOLD_TIMEOUT_MILLISECONDS = 45 * 60 * 1_000L
        const val POLL_MILLISECONDS = 5_000L
        const val TOOL_TIMEOUT_SECONDS = 60L

        val helperParentSource =
            """
            #include <errno.h>
            #include <signal.h>
            #include <spawn.h>
            #include <sys/wait.h>
            #include <unistd.h>

            extern char **environ;
            static pid_t child;

            static void forward(int number) {
              if (child > 0) {
                kill(child, number);
              }
            }

            int main(int argc, char **argv) {
              if (argc != 2) {
                return 64;
              }
              signal(SIGTERM, forward);
              signal(SIGINT, forward);
              char *arguments[] = {argv[1], NULL};
              if (posix_spawn(&child, argv[1], NULL, NULL, arguments, environ) != 0) {
                return 65;
              }
              int status = 0;
              while (waitpid(child, &status, 0) < 0) {
                if (errno != EINTR) {
                  return 66;
                }
              }
              if (WIFEXITED(status)) {
                return WEXITSTATUS(status);
              }
              return 128 + WTERMSIG(status);
            }
            """.trimIndent() + "\n"
    }
}
