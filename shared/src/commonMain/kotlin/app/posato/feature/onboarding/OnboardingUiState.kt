package app.posato.feature.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.posato.feature.onboarding.data.LocalSetupResult
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.onboarding.data.SetupCompletion
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import app.posato.feature.targets.ui.WebsiteBatchReceipt
import app.posato.feature.targets.ui.WebsiteBatchSubmission
import app.posato.feature.targets.ui.createWebsiteBatchSubmission
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class OnboardingStep {
    PURPOSE,
    PRIVACY,
    ICLOUD,
    PERMISSION,
    WEBSITE,
    SUMMARY
}

internal enum class OnboardingPermissionPlatform {
    IOS,
    MAC
}

internal data class OnboardingViewState(
    val step: OnboardingStep,
    val accessResult: ApplicationAccessResult?,
    val helperReadiness: MacHelperReadiness?,
    val savedWebsites: Int,
    val permissionRunning: Boolean,
    val websiteSaving: Boolean,
)

@Stable
internal class OnboardingUiState(
    private val setupStore: LocalSetupStore,
    private val policyStore: LocalTargetPolicyStore,
    private val applicationAccess: ApplicationAccessPort,
    private val helperSetup: MacHelperSetupUiState,
    private val scope: CoroutineScope,
) {
    var step by mutableStateOf(OnboardingStep.PURPOSE)
        private set
    var completion by mutableStateOf<SetupCompletion?>(null)
        private set
    var permissionRunning by mutableStateOf(false)
        private set
    var websiteSaving by mutableStateOf(false)
        private set
    var finishing by mutableStateOf(false)
        private set
    var accessResult by mutableStateOf<ApplicationAccessResult?>(null)
        private set
    val helperReadiness: MacHelperReadiness?
        get() {
            return helperSetup.readiness
        }
    var savedWebsites by mutableIntStateOf(0)
        private set

    fun snapshot(): OnboardingViewState {
        return OnboardingViewState(
            step = step,
            accessResult = accessResult,
            helperReadiness = helperReadiness,
            savedWebsites = savedWebsites,
            permissionRunning = permissionRunning || helperSetup.activity != null,
            websiteSaving = websiteSaving,
        )
    }

    fun loadCompletion() {
        scope.launch {
            completion = when (val result = setupStore.read()) {
                is LocalSetupResult.Success -> result.value
                is LocalSetupResult.Failure -> SetupCompletion.UNKNOWN
            }
        }
    }

    fun advance() {
        step = when (step) {
            OnboardingStep.PURPOSE -> OnboardingStep.PRIVACY
            OnboardingStep.PRIVACY -> OnboardingStep.ICLOUD
            OnboardingStep.ICLOUD -> OnboardingStep.PERMISSION
            OnboardingStep.PERMISSION -> OnboardingStep.WEBSITE
            OnboardingStep.WEBSITE, OnboardingStep.SUMMARY -> OnboardingStep.SUMMARY
        }
    }

    fun requestAccess() {
        if (permissionRunning) {
            return
        }
        permissionRunning = true
        scope.launch {
            try {
                accessResult = applicationAccess.requestAuthorization()
            } finally {
                permissionRunning = false
            }
        }
    }

    fun enableHelper() {
        if (permissionRunning) {
            return
        }
        helperSetup.enable()
    }

    fun recheckHelper() {
        if (permissionRunning) {
            return
        }
        helperSetup.check()
    }

    fun openHelperSettings() {
        helperSetup.openSettings()
    }

    fun submitWebsites(
        input: String,
        submissionId: Long,
        onReceipt: (WebsiteBatchReceipt?) -> Unit,
    ) {
        if (websiteSaving) {
            return
        }
        websiteSaving = true
        scope.launch {
            try {
                onReceipt(persistWebsites(input, submissionId))
            } finally {
                websiteSaving = false
            }
        }
    }

    fun refreshSavedWebsites() {
        scope.launch {
            when (val read = policyStore.read()) {
                is LocalPolicyResult.Success -> savedWebsites = read.value.policy.domains.size
                is LocalPolicyResult.Failure -> Unit
            }
        }
    }

    fun finish(onFinished: () -> Unit) {
        if (finishing) {
            return
        }
        finishing = true
        scope.launch {
            setupStore.markComplete()
            onFinished()
        }
    }

    private suspend fun persistWebsites(
        input: String,
        submissionId: Long,
    ): WebsiteBatchReceipt? {
        val snapshot = when (val read = policyStore.read()) {
            is LocalPolicyResult.Success -> read.value
            is LocalPolicyResult.Failure -> return WebsiteBatchReceipt(submissionId, saved = false)
        }
        val submission = createWebsiteBatchSubmission(
            input,
            snapshot.policy.domains.map { domain -> domain.canonicalValue },
        )
        if (submission is WebsiteBatchSubmission.TooLong) {
            return WebsiteBatchReceipt(submissionId, saved = false, tooLong = true)
        }
        val ready = submission as WebsiteBatchSubmission.Ready
        if (ready.addedCount == 0) {
            savedWebsites = snapshot.policy.domains.size
            return ready.toReceipt(submissionId, saved = true)
        }
        val policy = when (
            val validated = TargetPolicy.fromStoredValues(
                ready.canonicalDomains,
                snapshot.policy.applicationPolicyName?.canonicalValue,
            )
        ) {
            is TargetPolicyValidationResult.Success -> validated.policy
            is TargetPolicyValidationResult.Failure -> return WebsiteBatchReceipt(submissionId, saved = false)
        }
        return when (val replaced = policyStore.replace(snapshot.revision, policy)) {
            is LocalPolicyResult.Success -> {
                savedWebsites = replaced.value.policy.domains.size
                ready.toReceipt(submissionId, saved = true)
            }

            is LocalPolicyResult.Failure -> {
                WebsiteBatchReceipt(submissionId, saved = false)
            }
        }
    }
}

private fun WebsiteBatchSubmission.Ready.toReceipt(
    submissionId: Long,
    saved: Boolean,
): WebsiteBatchReceipt {
    return WebsiteBatchReceipt(
        submissionId = submissionId,
        saved = saved,
        addedCount = addedCount,
        duplicateCount = duplicateCount,
        rejectedIndices = rejectedIndices.toPersistentList(),
    )
}
