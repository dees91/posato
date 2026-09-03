package app.posato.control.core

import app.posato.control.model.Envelope
import app.posato.control.model.ErrorPayload
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvelopeAndStateTest {
    @Test
    fun `envelope keeps explicit nulls for result and error`() {
        val text = ControlJson.compact.encodeToString(Envelope.serializer(), Envelope(true, "status", "desktop", "run", 5))
        assertEquals(true, text.contains("\"result\":null"))
        assertEquals(true, text.contains("\"error\":null"))
        val failed = Envelope(false, "tap", null, "run", 5, error = ErrorPayload("ELEMENT_NOT_FOUND", "missing", "hint"))
        assertEquals(
            "hint",
            ControlJson.lenient.decodeFromString(
                Envelope.serializer(),
                ControlJson.compact.encodeToString(Envelope.serializer(), failed),
            ).error?.hint,
        )
    }

    @Test
    fun `error codes map to the documented exit codes`() {
        assertEquals(2, ErrorCode.USAGE.exitCode)
        assertEquals(3, ErrorCode.REFUSED_WITHOUT_CONFIRMATION.exitCode)
        assertEquals(4, ErrorCode.ELEMENT_NOT_FOUND.exitCode)
        assertEquals(5, ErrorCode.BUILD_FAILED.exitCode)
        assertEquals(6, ErrorCode.UNSUPPORTED_ON_TARGET.exitCode)
        assertEquals(1, ErrorCode.COMMAND_FAILED.exitCode)
    }

    @Test
    fun `run state survives a round-trip and tolerates a broken file`() {
        val root = createTempDirectory("posato-control-state")
        root.resolve("settings.gradle.kts").writeText("rootProject.name = \"Posato\"\n")
        val store = RunStateStore(RepoLayout(root))
        store.update(Target.SIMULATOR, LaunchedProcess(pid = 42, udid = "00000000-0000-0000-0000-000000000000", runId = "r1"))
        assertEquals(42, store.load().simulator?.pid)
        assertNull(store.load().desktop)
        RepoLayout(root).stateFile.writeText("{ not json")
        assertNull(store.load().simulator)
    }

    @Test
    fun `repo root discovery walks upwards to the Posato settings file`() {
        val root = createTempDirectory("posato-control-root")
        root.resolve("settings.gradle.kts").writeText("rootProject.name = \"Posato\"\n")
        val nested = root.resolve("tools").resolve("posato-control")
        java.nio.file.Files.createDirectories(nested)
        assertEquals(root, RepoLayout.discover(nested).root)
    }
}
