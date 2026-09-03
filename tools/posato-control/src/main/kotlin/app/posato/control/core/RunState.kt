package app.posato.control.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

@Serializable
data class LaunchedProcess(
    val pid: Long? = null,
    val startedAt: Long? = null,
    val udid: String? = null,
    val logPath: String? = null,
    val windowId: Long? = null,
    val runId: String? = null,
    val consolePid: Long? = null,
    val consoleStartedAt: Long? = null,
)

@Serializable
data class RunState(
    val desktop: LaunchedProcess? = null,
    val simulator: LaunchedProcess? = null,
    val device: LaunchedProcess? = null,
) {
    fun forTarget(target: Target): LaunchedProcess? = when (target) {
        Target.DESKTOP -> desktop
        Target.SIMULATOR -> simulator
        Target.DEVICE -> device
    }

    fun with(
        target: Target,
        process: LaunchedProcess?
    ): RunState = when (target) {
        Target.DESKTOP -> copy(desktop = process)
        Target.SIMULATOR -> copy(simulator = process)
        Target.DEVICE -> copy(device = process)
    }
}

class RunStateStore(
    private val layout: RepoLayout
) {
    fun load(): RunState {
        val file = layout.stateFile
        if (!file.exists()) return RunState()
        return try {
            ControlJson.lenient.decodeFromString(RunState.serializer(), file.readText())
        } catch (_: SerializationException) {
            RunState()
        }
    }

    fun save(state: RunState) {
        Files.createDirectories(layout.stateFile.parent)
        val temporary = layout.stateFile.resolveSibling(layout.stateFile.fileName.toString() + ".tmp")
        Files.writeString(temporary, ControlJson.pretty.encodeToString(RunState.serializer(), state))
        Files.move(temporary, layout.stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun update(
        target: Target,
        process: LaunchedProcess?
    ): RunState {
        val next = load().with(target, process)
        save(next)
        return next
    }
}

/**
 * Identifies a local process the tool started by its pid and start instant, so a recycled pid is
 * never mistaken for a tracked process.
 */
object TrackedProcess {
    fun startedAt(pid: Long): Long? = ProcessHandle.of(pid).flatMap { it.info().startInstant() }.map { it.toEpochMilli() }.orElse(null)

    fun handleOf(
        pid: Long?,
        startedAt: Long?
    ): ProcessHandle? {
        if (pid == null || startedAt == null) return null
        val handle = ProcessHandle.of(pid).orElse(null) ?: return null
        if (!handle.isAlive) return null
        val actual = handle.info().startInstant().map { it.toEpochMilli() }.orElse(null) ?: return null
        return handle.takeIf { actual == startedAt }
    }

    fun isAlive(
        pid: Long?,
        startedAt: Long?
    ): Boolean = handleOf(pid, startedAt) != null

    fun terminate(
        pid: Long?,
        startedAt: Long?
    ) {
        val handle = handleOf(pid, startedAt) ?: return
        handle.destroy()
        val deadline = System.currentTimeMillis() + GRACE_MS
        while (handle.isAlive && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        if (handle.isAlive) handle.destroyForcibly()
    }

    private const val GRACE_MS = 5_000L
    private const val POLL_MS = 100L
}
