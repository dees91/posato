package app.posato.feature.targets.ui

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.WebsiteWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The set screen's window rules: saving one replaces this set's rules in the store, which authors it after the
 * website on a linked device. A rule saves as a website change, so the screen shows it saving as one.
 */
internal class TargetsWindowRules(
    private val store: LocalTargetPolicyStore,
    private val setId: PauseSetId,
    private val policyState: MutableStateFlow<TargetsPolicyState>,
    private val submissionState: MutableStateFlow<TargetsSubmissionState>,
    private val scope: CoroutineScope,
    private val canMutate: () -> Boolean,
) {
    /** Sets [canonicalDomain]'s rule, or clears it when [window] is null; nothing changes for a website no longer in the set. */
    fun set(
        canonicalDomain: String,
        window: WebsiteWindow?,
    ) {
        val snapshot = policyState.value.snapshot ?: return
        val domain = ExactDomain.restore(canonicalDomain) ?: return
        val sets = snapshot.sets.withWindow(setId, domain, window) ?: return
        if (!canMutate() || sets == snapshot.sets || submissionState.value is TargetsSubmissionState.Saving) {
            return
        }
        submissionState.update { TargetsSubmissionState.Saving(TargetMutation.DOMAIN) }
        scope.launch {
            try {
                when (val result = store.replaceSets(snapshot.revision, sets)) {
                    is LocalPolicyResult.Success -> {
                        policyState.update { TargetsPolicyState(snapshot = result.value) }
                        submissionState.update { TargetsSubmissionState.Idle }
                    }

                    is LocalPolicyResult.Failure -> {
                        submissionState.update { TargetsSubmissionState.Failed(result.reason.toSaveFailure()) }
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (_: Exception) {
                submissionState.update { TargetsSubmissionState.Failed(TargetsOperationFailure.SAVE_FAILED) }
            } finally {
                submissionState.update { state -> if (state is TargetsSubmissionState.Saving) TargetsSubmissionState.Idle else state }
            }
        }
    }
}
