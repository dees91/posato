package app.posato.control.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText

@Serializable
data class LaunchedProcess(
    val pid: Long? = null,
    val udid: String? = null,
    val logPath: String? = null,
    val windowId: Long? = null,
    val runId: String? = null,
    val consolePid: Long? = null,
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
        Files.writeString(layout.stateFile, ControlJson.pretty.encodeToString(RunState.serializer(), state))
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
