package app.posato.control.backend

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.model.DoctorCheck
import app.posato.control.model.Query
import app.posato.control.model.RunResult
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class BuildOptions(
    val signingIdentity: String? = null,
    val verify: Boolean = false,
    val driver: Boolean = false,
)

@Serializable
data class BuildResult(
    val appPath: String? = null,
    val signingMode: String? = null,
    val driverTestRun: String? = null,
    val durationMs: Long,
)

@Serializable
data class LaunchOptions(
    val fresh: Boolean = false,
    val captureLogs: Boolean = false,
    val build: Boolean = false,
    val arguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
)

@Serializable
data class LaunchResult(
    val pid: Long? = null,
    val udid: String? = null,
    val logPath: String? = null,
    val windowId: Long? = null,
)

@Serializable
data class StatusResult(
    val installed: Boolean,
    val running: Boolean,
    val pid: Long? = null,
    val udid: String? = null,
    val appPath: String? = null,
    val containerPath: String? = null,
    val signingMode: String? = null,
    val windowId: Long? = null,
    val logPath: String? = null,
    val lastRunId: String? = null,
)

@Serializable
data class ResetPlan(
    val deletions: List<String>,
    val uninstall: Boolean,
    val performed: Boolean,
    val backupDirectory: String? = null,
)

@Serializable
data class LogsResult(
    val logPath: String? = null,
    val lines: List<String> = emptyList(),
)

interface Lifecycle {
    fun doctor(): List<DoctorCheck>

    fun build(options: BuildOptions): BuildResult

    fun install(): StatusResult

    fun launch(options: LaunchOptions): LaunchResult

    fun terminate(): StatusResult

    fun status(): StatusResult
}

interface Evidence {
    fun screenshot(
        name: String,
        out: Path?
    ): Path

    fun logs(
        tail: Int,
        streamSeconds: Int?
    ): LogsResult

    fun databasePaths(): List<Path>

    fun reset(
        dryRun: Boolean,
        keepInstall: Boolean
    ): ResetPlan

    fun cleanup(
        dryRun: Boolean,
        purgeDerivedData: Boolean
    ): List<String>
}

interface Interaction {
    fun snapshot(
        root: Query?,
        maxDepth: Int?
    ): SnapshotNode

    fun runScenario(scenario: Scenario): RunResult

    /**
     * Types into whatever the addressed process has focused, without resolving an element. A window that exposes no
     * accessibility tree, such as the macOS helper's open panel, can be reached no other way.
     */
    fun typeFocused(
        text: String,
        clear: Boolean,
        submit: Boolean
    ): Unit = throw ControlException(
        ErrorCode.UNSUPPORTED_ON_TARGET,
        "Typing into the focused element without a query is a desktop capability.",
        "Pass an element query, or use -t desktop with --process.",
    )

    /** Waits until the addressed process owns a visible window: the readiness gate for a window with no element tree. */
    fun awaitWindow(timeoutSeconds: Double): Unit = throw ControlException(
        ErrorCode.UNSUPPORTED_ON_TARGET,
        "Waiting for a process window is a desktop capability.",
        "Pass an element query, or use -t desktop with --process.",
    )
}

interface Backend :
    Lifecycle,
    Evidence,
    Interaction {
    val target: Target
}
