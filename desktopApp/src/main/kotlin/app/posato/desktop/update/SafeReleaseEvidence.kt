package app.posato.desktop.update

internal enum class InstallerObservation {
    TERMINATED,
    RUNNING,
    UNKNOWN,
}

internal data class GateBuilds(
    val fromBuild: String,
    val targetBuild: String,
)

internal data class BundleIdentity(
    val runningBuild: String,
    val onDiskBuild: String?,
    val signedByTeam: Boolean,
)

internal fun combineInstallerObservations(
    domains: List<LaunchctlJobState>,
    autoupdateRunning: Boolean?,
): InstallerObservation {
    return when {
        LaunchctlJobState.RUNNING in domains || autoupdateRunning == true -> {
            InstallerObservation.RUNNING
        }

        domains.isEmpty() || LaunchctlJobState.UNKNOWN in domains || autoupdateRunning == null -> {
            InstallerObservation.UNKNOWN
        }

        else -> {
            InstallerObservation.TERMINATED
        }
    }
}

internal fun replacementSettled(
    gate: GateBuilds,
    installer: InstallerObservation,
    identity: BundleIdentity,
): Boolean {
    if (installer != InstallerObservation.TERMINATED) {
        return false
    }
    if (identity.onDiskBuild != identity.runningBuild || !identity.signedByTeam) {
        return false
    }
    val running = identity.runningBuild
    return running == gate.fromBuild || running == gate.targetBuild || isNumericallyNewer(running, gate.fromBuild)
}

private fun isNumericallyNewer(
    candidate: String,
    baseline: String,
): Boolean {
    val candidateNumber = candidate.toBuildNumber() ?: return false
    val baselineNumber = baseline.toBuildNumber() ?: return false
    return candidateNumber > baselineNumber
}

private fun String.toBuildNumber(): Long? {
    return if (BUILD_NUMBER.matches(this)) toLongOrNull() else null
}

private val BUILD_NUMBER = Regex("[1-9][0-9]{0,17}")
