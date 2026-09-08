package app.posato.feature.sync.bootstrap

internal enum class EstablishedStatus {
    READY,
    LOCAL_ONLY,
    ZONE_MISSING,
    DIFFERENT_ANCHOR,
    RETRYABLE,
    ACTION_REQUIRED,
}

internal data class EstablishedCheck(
    val status: EstablishedStatus,
    val workspace: EstablishedWorkspace? = null,
) {
    override fun toString(): String {
        return "EstablishedCheck(redacted)"
    }
}

internal suspend fun BootstrapCloudPort.checkEstablished(workspace: EstablishedWorkspace): EstablishedCheck {
    val status = when (fetchZone(workspace.binding)) {
        ZoneFetchResult.Found -> checkEstablishedAnchor(workspace)
        ZoneFetchResult.Missing -> EstablishedStatus.ZONE_MISSING
        ZoneFetchResult.Retryable, ZoneFetchResult.UnknownOutcome -> EstablishedStatus.RETRYABLE
        ZoneFetchResult.AccountChanged -> EstablishedStatus.ACTION_REQUIRED
    }
    return EstablishedCheck(status, workspace)
}

private suspend fun BootstrapCloudPort.checkEstablishedAnchor(workspace: EstablishedWorkspace): EstablishedStatus {
    return when (val result = readAnchor(workspace.binding)) {
        is AnchorReadResult.Found -> {
            val context = workspace.context
            val expected = WorkspaceAnchor(context.workspaceId, context.transportEpochId, context.keyEpochId)
            if (result.anchor == expected) EstablishedStatus.READY else EstablishedStatus.DIFFERENT_ANCHOR
        }

        AnchorReadResult.Retryable, AnchorReadResult.UnknownOutcome -> {
            EstablishedStatus.RETRYABLE
        }

        AnchorReadResult.Missing, AnchorReadResult.IntegrityFailure, AnchorReadResult.AccountChanged -> {
            EstablishedStatus.ACTION_REQUIRED
        }
    }
}
