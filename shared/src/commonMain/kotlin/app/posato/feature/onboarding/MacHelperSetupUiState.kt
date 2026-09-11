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

    fun presentation(): MacSetupPresentation {
        return MacSetupPresentation(readiness = readiness, activity = activity, completedOperations = completedOperations)
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
