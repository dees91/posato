package app.posato.control.desktop

import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import kotlinx.serialization.Serializable
import java.time.Duration

@Serializable
data class ProcessResources(
    val name: String,
    val pid: Long,
    val footprintMegabytes: Double?,
    val cpuSeconds: Double,
    val idleWakeupsPerSecond: Double?,
)

@Serializable
data class ResourceSample(
    val seconds: Int,
    val processes: List<ProcessResources>,
)

/** Drives the resident status item, the window, and resource sampling for a windowless application. */
class DesktopPresenceDriver(
    private val context: RunContext,
) {
    private val bridge = AxBridge(context)
    private val processes = DesktopProcesses(context)
    private val stateStore = RunStateStore(context.layout)

    fun menu(
        mode: String,
        title: String?
    ): StatusMenuResult = bridge.statusMenu(applicationPid(), mode, title)

    fun closeWindow() {
        bridge.closeWindow(applicationPid())
    }

    fun resources(seconds: Int): ResourceSample {
        val targets = buildList {
            add("Posato" to applicationPid())
            helperPid()?.let { add("PosatoMacOSHelper" to it) }
        }
        val before = targets.associate { (_, pid) -> pid to cpuSeconds(pid) }
        val wakeups = idleWakeups(targets.map { it.second }, seconds)
        val samples = targets.map { (name, pid) ->
            ProcessResources(
                name = name,
                pid = pid,
                footprintMegabytes = footprint(pid),
                cpuSeconds = cpuSeconds(pid) - (before[pid] ?: 0.0),
                idleWakeupsPerSecond = wakeups[pid],
            )
        }
        return ResourceSample(seconds, samples)
    }

    private fun applicationPid(): Long = processes.resolveTarget(null, stateStore.load().desktop)

    private fun helperPid(): Long? = processes.containedProcesses().firstOrNull { it.command.endsWith("/PosatoMacOSHelper") }?.pid

    private fun cpuSeconds(pid: Long): Double {
        val output = context.subprocess.run(listOf("/bin/ps", "-o", "time=", "-p", pid.toString())).stdout.trim()
        val parts = output.split(":").mapNotNull { it.toDoubleOrNull() }
        return parts.fold(0.0) { total, part -> total * SECONDS_PER_MINUTE + part }
    }

    private fun footprint(pid: Long): Double? {
        val output = context.subprocess.run(listOf("/usr/bin/footprint", "-p", pid.toString())).stdout
        val match = FOOTPRINT.find(output) ?: return null
        val value = match.groupValues[1].toDouble()
        return if (match.groupValues[2] == "KB") value / KILOBYTES_PER_MEGABYTE else value
    }

    private fun idleWakeups(
        pids: List<Long>,
        seconds: Int
    ): Map<Long, Double> {
        val arguments = listOf("/usr/bin/top", "-l", "2", "-s", seconds.toString()) +
            pids.flatMap { listOf("-pid", it.toString()) } +
            listOf("-stats", "pid,idlew")
        val samples = context.subprocess.run(arguments, timeout = Duration.ofSeconds(seconds + TOP_GRACE_SECONDS)).stdout.lineSequence()
            .mapNotNull { line -> ROW.matchEntire(line.trim())?.let { it.groupValues[1].toLong() to it.groupValues[2].toLong() } }
            .groupBy({ it.first }, { it.second })
        return samples.filterValues { it.size >= 2 }.mapValues { (_, values) -> (values.last() - values.first()).toDouble() / seconds }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val TOP_GRACE_SECONDS = 60L
        const val KILOBYTES_PER_MEGABYTE = 1024.0
        val FOOTPRINT = Regex("""phys_footprint:\s+([0-9.]+)\s+(MB|KB)""")
        val ROW = Regex("""(\d+)\s+(\d+)[+-]?""")
    }
}
