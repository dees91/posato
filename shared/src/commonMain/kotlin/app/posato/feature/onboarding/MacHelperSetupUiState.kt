package app.posato.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_setup_checking
import app.posato.generated.resources.mac_setup_enabling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

internal enum class MacSetupActivity {
    CHECKING,
    ENABLING,
}

internal fun MacSetupActivity.label(): StringResource {
    return when (this) {
        MacSetupActivity.CHECKING -> Res.string.mac_setup_checking
        MacSetupActivity.ENABLING -> Res.string.mac_setup_enabling
    }
}

internal data class MacSetupPresentation(
    val readiness: MacHelperReadiness? = null,
    val activity: MacSetupActivity? = null,
    val completedOperations: Long = 0,
    val repeatedResult: Boolean = false,
)

@Stable
internal class MacHelperSetupUiState(
    private val macHelper: MacHelperPort,
    private val scope: CoroutineScope,
) {
    var readiness by mutableStateOf<MacHelperReadiness?>(null)
        private set
    var activity by mutableStateOf<MacSetupActivity?>(null)
        private set
    private var completedOperations by mutableLongStateOf(0)
    private var repeatedResult by mutableStateOf(false)

    /**
     * Answers already reported since the helper was last ready. A failing Enable alternates between
     * two answers rather than repeating one, so a consecutive-identical check would never see it.
     */
    private val reportedAnswers = mutableSetOf<MacHelperReadiness>()

    fun presentation(): MacSetupPresentation {
        return MacSetupPresentation(
            readiness = readiness,
            activity = activity,
            completedOperations = completedOperations,
            repeatedResult = repeatedResult,
        )
    }

    fun check() {
        run(MacSetupActivity.CHECKING, macHelper::recheck)
    }

    fun enable() {
        run(MacSetupActivity.ENABLING, macHelper::enable)
    }

    fun openSettings() {
        macHelper.openApprovalSettings()
    }

    private fun run(
        next: MacSetupActivity,
        action: suspend () -> MacHelperReadiness,
    ) {
        if (activity != null) {
            return
        }
        activity = next
        scope.launch {
            try {
                val answer = action()
                repeatedResult = answer != MacHelperReadiness.READY && !reportedAnswers.add(answer)
                if (answer == MacHelperReadiness.READY) {
                    reportedAnswers.clear()
                }
                readiness = answer
                completedOperations += 1
            } finally {
                activity = null
            }
        }
    }
}

@Composable
internal fun rememberMacHelperSetupUiState(macHelper: MacHelperPort): MacHelperSetupUiState {
    val scope = rememberCoroutineScope()
    return remember(macHelper) { MacHelperSetupUiState(macHelper, scope) }
}
