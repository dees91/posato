package app.posato.desktop.update

internal enum class LaunchctlJobState {
    ABSENT,
    NOT_RUNNING,
    RUNNING,
    UNKNOWN,
}

internal fun parseLaunchctlPrint(
    label: String,
    exitCode: Int,
    output: String,
): LaunchctlJobState {
    return when (exitCode) {
        LAUNCHCTL_NOT_FOUND_EXIT -> parseNotFound(label, output)
        0 -> parseLoaded(label, output)
        else -> LaunchctlJobState.UNKNOWN
    }
}

private fun parseNotFound(
    label: String,
    output: String,
): LaunchctlJobState {
    val lines = output.trimEnd('\n').split('\n')
    val recognized = lines.size == 2 &&
        lines[0] == "Bad request." &&
        lines[1].startsWith("Could not find service \"$label\" in domain for ")
    return if (recognized) LaunchctlJobState.ABSENT else LaunchctlJobState.UNKNOWN
}

private fun parseLoaded(
    label: String,
    output: String,
): LaunchctlJobState {
    val lines = output.split('\n')
    val header = lines.firstOrNull() ?: return LaunchctlJobState.UNKNOWN
    if (!header.endsWith("/$label = {")) {
        return LaunchctlJobState.UNKNOWN
    }
    val topLevel = lines.drop(1).filter { line -> line.startsWith("\t") && !line.startsWith("\t\t") }
    val states = topLevel.filter { line -> line.startsWith("\tstate = ") }
    val hasPid = topLevel.any { line -> TOP_LEVEL_PID.matches(line) }
    return when (states.singleOrNull()) {
        "\tstate = running" -> if (hasPid) LaunchctlJobState.RUNNING else LaunchctlJobState.UNKNOWN
        "\tstate = not running" -> if (hasPid) LaunchctlJobState.UNKNOWN else LaunchctlJobState.NOT_RUNNING
        else -> LaunchctlJobState.UNKNOWN
    }
}

private val TOP_LEVEL_PID = Regex("\tpid = [0-9]+")

private const val LAUNCHCTL_NOT_FOUND_EXIT: Int = 113
