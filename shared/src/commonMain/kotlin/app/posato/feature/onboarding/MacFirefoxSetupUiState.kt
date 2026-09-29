package app.posato.feature.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class MacFirefoxUi(
    val detected: Boolean,
    val applicationName: String?,
    val verified: Boolean,
    val checking: Boolean = false,
)

@Stable
internal class MacFirefoxSetupUiState(
    private val macHelper: MacHelperPort,
    private val scope: CoroutineScope,
) {
    private var ui by mutableStateOf<MacFirefoxUi?>(null)
    private var checking by mutableStateOf(false)

    fun presentation(): MacFirefoxUi? {
        return ui?.copy(checking = checking)
    }

    fun install() {
        if (checking) {
            return
        }
        checking = true
        scope.launch {
            try {
                if (macHelper.firefoxExtension?.install() == true) {
                    refresh { true }
                }
            } finally {
                checking = false
            }
        }
    }

    fun recheck() {
        if (checking) {
            return
        }
        checking = true
        scope.launch {
            try {
                refresh { true }
            } finally {
                checking = false
            }
        }
    }

    suspend fun refresh(current: () -> Boolean) {
        val port = macHelper.firefoxExtension ?: return
        val detection = port.detect()
        val seen = if (detection.installed) port.verified() else false
        if (current()) {
            ui = MacFirefoxUi(detection.installed, detection.applicationName, seen)
        }
    }
}
