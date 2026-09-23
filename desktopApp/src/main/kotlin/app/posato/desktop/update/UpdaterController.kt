package app.posato.desktop.update

import app.posato.feature.update.MaintenanceReopenResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

internal interface UpdateAdmission {
    suspend fun admit(targetBuild: String): AdmissionOutcome

    suspend fun admitPendingInstallation(targetBuild: String): AdmissionOutcome

    suspend fun onCycleEnded()

    suspend fun evaluateRelease(): MaintenanceReopenResult

    suspend fun restoreMaintenanceAtStartup(): Boolean
}

internal fun interface UpdaterReplies {
    fun completeAdmission(
        token: Long,
        granted: Boolean,
        refusal: AdmissionRefusal?,
    )
}

internal enum class InstallRequestStage {
    NEW_INSTALLATION,
    PENDING_INSTALLATION,
}

internal class UpdaterController(
    private val scope: CoroutineScope,
    private val admission: UpdateAdmission,
    private val replies: UpdaterReplies,
) {
    private val cycleGeneration = AtomicLong()
    private var releaseJob: Job? = null

    suspend fun start() {
        if (admission.restoreMaintenanceAtStartup()) {
            evaluateReleaseUntilSettled()
        }
    }

    fun onInstallRequested(
        token: Long,
        targetBuild: String,
        stage: InstallRequestStage,
    ) {
        val requestedIn = cycleGeneration.get()
        scope.launch {
            val outcome = decide(targetBuild, stage)
            val sameCycle = cycleGeneration.get() == requestedIn
            replies.completeAdmission(token, outcome == AdmissionOutcome.Admitted && sameCycle, (outcome as? AdmissionOutcome.Refused)?.reason)
            if (outcome == AdmissionOutcome.Admitted && !sameCycle) {
                admission.onCycleEnded()
            }
            if (outcome != AdmissionOutcome.Admitted || !sameCycle) {
                evaluateReleaseUntilSettled()
            }
        }
    }

    private suspend fun decide(
        targetBuild: String,
        stage: InstallRequestStage,
    ): AdmissionOutcome {
        return try {
            when (stage) {
                InstallRequestStage.NEW_INSTALLATION -> admission.admit(targetBuild)
                InstallRequestStage.PENDING_INSTALLATION -> admission.admitPendingInstallation(targetBuild)
            }
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            AdmissionOutcome.Refused(AdmissionRefusal.CLEANUP_UNCERTAIN)
        }
    }

    fun onCycleFinished() {
        cycleGeneration.incrementAndGet()
        scope.launch {
            admission.onCycleEnded()
            evaluateReleaseUntilSettled()
        }
    }

    private fun evaluateReleaseUntilSettled() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            var attempt = 0
            while (!admission.evaluateRelease().endsEvaluation()) {
                delay(if (attempt < FAST_RELEASE_ATTEMPTS) FAST_RELEASE_DELAY_MILLIS else SLOW_RELEASE_DELAY_MILLIS)
                attempt += 1
            }
        }
    }
}

private fun MaintenanceReopenResult.endsEvaluation(): Boolean {
    return this == MaintenanceReopenResult.Reopened ||
        this == MaintenanceReopenResult.AlreadyOpen ||
        this == MaintenanceReopenResult.CycleInProgress
}

private const val FAST_RELEASE_ATTEMPTS: Int = 30
private const val FAST_RELEASE_DELAY_MILLIS: Long = 2_000L
private const val SLOW_RELEASE_DELAY_MILLIS: Long = 60_000L
