package app.posato

import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsScreen
import dev.zacsweers.metro.Inject

@Inject
class PosatoApplication internal constructor(
    private val store: LocalTargetPolicyStore,
    private val applicationMappings: LocalApplicationMappings,
) {
    @Composable
    fun Content() {
        PosatoTheme {
            TargetsScreen(store, applicationMappings)
        }
    }
}
