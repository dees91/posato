package app.posato.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class MacSetupActivity {
    CHECKING,
    ENABLING
}

internal data class MacSetupPresentation(
    val readiness: MacHelperReadiness? = null,
    val activity: MacSetupActivity? = null,
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

    fun presentation(): MacSetupPresentation {
        return MacSetupPresentation(readiness = readiness, activity = activity)
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
                readiness = action()
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
