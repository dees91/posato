package app.posato.desktop.macos

import app.posato.feature.targets.data.LocalApplicationMappingId
import kotlinx.coroutines.runBlocking
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
 * Maintainer-gated physical harness for the MACOS-005 checklist. It runs only when the
 * `POSATO_MACOS_005_PHYSICAL` environment variable is set, drives the installed
 * development-signed package, and needs the maintainer at the Mac: the first notice raises the
 * notification authorization prompt, which the maintainer allows once. The gate is not a Gradle
 * input, so run the task with `--rerun`.
 *
 * The harness builds two disposable development-signed test applications (selected target and
 * control), extracts the selected binary designated-requirement blob with `codesign`/`csreq`,
 * and feeds it through the test-only resolver seam on the orchestrator; the `TARGETS-003`
 * database is never touched. Marker files under `build/verification/macos-005` drive the run:
 * `ENABLE_APPROVED` after the System Settings approval, `NOTIFY_GO` after the maintainer allows
 * notifications and sees the paused notice, and `ABORT` to stop early.
 */
class MacOsApplicationPhysicalHarnessTest {
    @Test
    fun `given the physical gate when unset then the maintainer checklist is skipped`() {
        if (!gateIsSet()) {
            return
        }
        val checklist = evidenceDirectory().resolve("checklist.md")
        Files.writeString(checklist, checklistTemplate)
        assertTrue(Files.isRegularFile(checklist))
    }

    @Test
    fun `given the physical gate when set then selected apps terminate and control survives`() {
        if (!gateIsSet()) {
            return
        }
        val run = PhysicalRun(evidenceDirectory())
        run.reset()
        val helperPath = Path.of(System.getenv(HELPER_ENV) ?: DEFAULT_HELPER_PATH)
        assertTrue(Files.isExecutable(helperPath), "Installed helper is missing: $helperPath")
        val identity = signingIdentity()
        val selected = buildTestApplication(run.directory, "PosatoTestSelected", "com.example.PosatoTestSelected", identity)
        val control = buildTestApplication(run.directory, "PosatoTestControl", "com.example.PosatoTestControl", identity)
        val requirement = designatedRequirementBlob(run.directory, selected)
        run.record("test applications signed, selected blob=${requirement.size} bytes")
        val parentProcess = signedParentProcess(run.directory, identity)
        quitApplication(selected)
        quitApplication(control)
        val client = MacOsHelperClient(helperPath = helperPath, launchPrefix = listOf(parentProcess.toString()))
        try {
            holdActive(client, run, requirement, selected, control)
            client.close()
            val afterExitPid = launchApplication(selected)
            Thread.sleep(GRACE_MILLISECONDS)
            assertTrue(isRunning(afterExitPid), "Observation survived parent exit")
            run.record("parent-exit row passed")
        } finally {
            runCatching { client.close() }
            quitApplication(selected)
            quitApplication(control)
        }
        run.record("harness done")
    }

    private fun holdActive(
        client: MacOsHelperClient,
        run: PhysicalRun,
        requirement: ByteArray,
        selected: Path,
        control: Path,
    ) {
        val enabled = waitForEnable(client, run)
        assertEquals(HelperResult.Outcome.Success, enabled.outcome, "Helper was not enabled")
        val mappingId = LocalApplicationMappingId.restore("aa".repeat(32)) ?: error("Synthetic mapping id is invalid")
        val enforcer = MacOsApplicationEnforcer(
            commands = client,
            requirements = { ids ->
                assertEquals(listOf(mappingId), ids)
                listOf(requirement)
            },
        )
        val started = runBlocking {
            enforcer.start(listOf(mappingId), System.currentTimeMillis() + SESSION_MILLISECONDS)
        }
        val active = assertIs<ApplicationEnforcementResult.Active>(started, "Enforcement did not activate")
        assertEquals(1, active.acceptedCount)
        run.record("active accepted=${active.acceptedCount}")

        val selectedPid = launchApplication(selected)
        assertTrue(waitForExit(selectedPid, GRACE_MILLISECONDS), "Selected application survived launch during the session")
        run.record("launch-during row passed")
        run.record("waiting for NOTIFY_GO: allow notifications once and confirm the paused notice, then create it")
        run.waitFor(run.notifyGo, NOTIFY_TIMEOUT_MILLISECONDS)

        val controlPid = launchApplication(control)
        Thread.sleep(GRACE_MILLISECONDS)
        assertTrue(isRunning(controlPid), "Control application was terminated")
        run.record("control row passed")

        val cleared = runBlocking { enforcer.clear() }
        assertEquals(HelperResult.Outcome.Success, cleared.outcome, "Enforcement was not cleared")

        val alreadyRunningPid = launchApplication(selected)
        Thread.sleep(LAUNCH_SETTLE_MILLISECONDS)
        assertTrue(isRunning(alreadyRunningPid), "Selected application did not launch for the activation row")
        val reactivated = runBlocking { enforcer.start(listOf(mappingId), null) }
        assertIs<ApplicationEnforcementResult.Active>(reactivated, "Enforcement did not reactivate")
        assertTrue(waitForExit(alreadyRunningPid, GRACE_MILLISECONDS), "Running application survived activation")
        run.record("running-at-activation row passed")

        val recleared = runBlocking { enforcer.clear() }
        assertEquals(HelperResult.Outcome.Success, recleared.outcome, "Enforcement was not cleared again")
        val relaunchedPid = launchApplication(selected)
        Thread.sleep(GRACE_MILLISECONDS)
        assertTrue(isRunning(relaunchedPid), "Selected application was terminated after clear")
        run.record("clear row passed")
        quitApplication(selected)

        val rearmed = runBlocking { enforcer.start(listOf(mappingId), null) }
        assertIs<ApplicationEnforcementResult.Active>(rearmed, "Enforcement did not rearm")
        destroyHelper(client)
        val orphanPid = launchApplication(selected)
        Thread.sleep(GRACE_MILLISECONDS)
        assertTrue(isRunning(orphanPid), "Observation survived forced helper termination")
        run.record("forced-termination row passed")
        quitApplication(selected)
        quitApplication(control)
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

    private fun buildTestApplication(
        directory: Path,
        name: String,
        bundleIdentifier: String,
        identity: String,
    ): Path {
        val bundle = directory.resolve("$name.app")
        val contents = bundle.resolve("Contents")
        val executableDirectory = contents.resolve("MacOS")
        Files.createDirectories(executableDirectory)
        Files.writeString(
            contents.resolve("Info.plist"),
            testApplicationPropertyList(name, bundleIdentifier),
        )
        val source = directory.resolve("$name.m")
        Files.writeString(source, testApplicationSource)
        val executable = executableDirectory.resolve(name)
        runTool("/usr/bin/cc", "-Wall", "-Werror", "-framework", "AppKit", "-o", executable.toString(), source.toString())
        runTool(
            "/usr/bin/codesign",
            "--force",
            "--sign",
            identity,
            "--identifier",
            bundleIdentifier,
            bundle.toString(),
        )
        return bundle
    }

    private fun designatedRequirementBlob(
        directory: Path,
        bundle: Path,
    ): ByteArray {
        val text = directory.resolve("designated-requirement.txt")
        val blob = directory.resolve("designated-requirement.bin")
        val describe = ProcessBuilder("/usr/bin/codesign", "-d", "-r-", bundle.toString())
            .redirectErrorStream(true)
            .start()
        val printed = describe.inputStream.readAllBytes().decodeToString()
        check(describe.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS) && describe.exitValue() == 0) {
            "codesign -d failed: $printed"
        }
        val requirement = printed.lines().firstOrNull { line -> line.startsWith("designated => ") }
            ?.substringAfter("designated => ")
            ?.trim()
            ?: error("Designated requirement was not printed: $printed")
        Files.writeString(text, requirement)
        runTool("/usr/bin/csreq", "-r=$requirement", "-b", blob.toString())
        return Files.readAllBytes(blob)
    }

    private fun launchApplication(bundle: Path): Long {
        val process = ProcessBuilder("/usr/bin/open", bundle.toString()).redirectErrorStream(true).start()
        check(process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "open failed for $bundle"
        }
        val deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT_MILLISECONDS
        while (System.currentTimeMillis() <= deadline) {
            findPid(bundle)?.let { pid ->
                return pid
            }
            Thread.sleep(POLL_MILLISECONDS)
        }
        error("Test application did not appear: $bundle")
    }

    private fun findPid(bundle: Path): Long? {
        val name = bundle.fileName.toString().removeSuffix(".app")
        val process = ProcessBuilder("/usr/bin/pgrep", "-f", "$name.app/Contents/MacOS/$name")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readAllBytes().decodeToString().trim()
        process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return output.lines().firstOrNull()?.toLongOrNull()
    }

    private fun waitForExit(
        pid: Long,
        timeoutMilliseconds: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMilliseconds
        while (isRunning(pid) && System.currentTimeMillis() <= deadline) {
            Thread.sleep(POLL_MILLISECONDS)
        }
        return !isRunning(pid)
    }

    private fun isRunning(pid: Long): Boolean {
        return ProcessHandle.of(pid).map { handle -> handle.isAlive }.orElse(false)
    }

    private fun quitApplication(bundle: Path) {
        findPid(bundle)?.let { pid ->
            ProcessHandle.of(pid).ifPresent { handle -> handle.destroy() }
        }
    }

    private fun destroyHelper(client: MacOsHelperClient) {
        client.destroySpawnedHelper()
    }

    private fun signingIdentity(): String {
        return System.getenv(SIGNING_IDENTITY_ENV)?.takeIf { value -> value.isNotBlank() }
            ?: localProperty(SIGNING_IDENTITY_PROPERTY)
            ?: error("Signing identity is absent: set $SIGNING_IDENTITY_ENV or $SIGNING_IDENTITY_PROPERTY")
    }

    private fun signedParentProcess(
        directory: Path,
        identity: String,
    ): Path {
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
        return Files.createDirectories(repositoryRoot().resolve("build/verification/macos-005"))
    }

    private fun repositoryRoot(): Path {
        return generateSequence(Path.of("").toAbsolutePath()) { path -> path.parent }
            .firstOrNull { candidate -> Files.isRegularFile(candidate.resolve("settings.gradle.kts")) }
            ?: error("Repository root was not found")
    }

    private fun HelperResult.categories(): String {
        return "outcome=$outcome state=$serviceState phase=$ownershipPhase action=$requiredAction failure=$failure"
    }

    private class PhysicalRun(
        val directory: Path,
    ) {
        val runLog: Path = directory.resolve("run.md")
        val abortMarker: Path = directory.resolve("ABORT")
        val enableApproved: Path = directory.resolve("ENABLE_APPROVED")
        val notifyGo: Path = directory.resolve("NOTIFY_GO")

        fun reset() {
            listOf(abortMarker, enableApproved, notifyGo).forEach { marker ->
                Files.deleteIfExists(marker)
            }
            if (!Files.isRegularFile(runLog)) {
                Files.writeString(runLog, "# MACOS-005 physical run\n")
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
        const val GATE = "POSATO_MACOS_005_PHYSICAL"
        const val HELPER_ENV = "POSATO_MACOS_005_HELPER"
        const val SIGNING_IDENTITY_ENV = "POSATO_MACOS_SIGNING_IDENTITY"
        const val SIGNING_IDENTITY_PROPERTY = "posato.macos.signingIdentity"
        const val APPLICATION_IDENTIFIER = "app.posato.macos"
        const val DEFAULT_HELPER_PATH = "/Applications/Posato.app/Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper"
        const val SESSION_MILLISECONDS = 30 * 60 * 1_000L
        const val GRACE_MILLISECONDS = 12_000L
        const val LAUNCH_SETTLE_MILLISECONDS = 5_000L
        const val LAUNCH_TIMEOUT_MILLISECONDS = 60_000L
        const val ENABLE_TIMEOUT_MILLISECONDS = 10 * 60 * 1_000L
        const val NOTIFY_TIMEOUT_MILLISECONDS = 15 * 60 * 1_000L
        const val POLL_MILLISECONDS = 500L
        const val TOOL_TIMEOUT_SECONDS = 60L

        val checklistTemplate =
            """
            # MACOS-005 physical checklist

            - [ ] Helper enabled under System Settings > General > Login Items (`ENABLE_APPROVED`)
            - [ ] Selected test application terminated within the grace after launch
            - [ ] Paused notice observed once, notifications allowed (`NOTIFY_GO`)
            - [ ] Control test application launched and kept running
            - [ ] Selected application already running at activation was terminated
            - [ ] After clear, the selected application launches and keeps running
            - [ ] Forced helper termination stops observation (selected survives)
            - [ ] Parent exit stops observation (selected survives)
            - [ ] Synthetic canary absent from the helper log, evidence, and client output
            """.trimIndent() + "\n"

        fun testApplicationPropertyList(
            name: String,
            bundleIdentifier: String,
        ): String {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleExecutable</key>
                    <string>$name</string>
                    <key>CFBundleIdentifier</key>
                    <string>$bundleIdentifier</string>
                    <key>CFBundleName</key>
                    <string>$name</string>
                    <key>CFBundlePackageType</key>
                    <string>APPL</string>
                    <key>CFBundleShortVersionString</key>
                    <string>1.0</string>
                    <key>LSUIElement</key>
                    <true/>
                </dict>
                </plist>
                """.trimIndent()
        }

        val testApplicationSource =
            """
            #import <AppKit/AppKit.h>

            int main(int argc, const char *argv[]) {
              @autoreleasepool {
                [NSApplication sharedApplication];
                [NSApp run];
              }
              return 0;
            }
            """.trimIndent()

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
              int status = posix_spawn(&child, argv[1], NULL, NULL, arguments, environ);
              if (status != 0) {
                return 1;
              }
              while (waitpid(child, &status, 0) < 0 && errno == EINTR) {
              }
              return 0;
            }
            """.trimIndent()
    }
}
