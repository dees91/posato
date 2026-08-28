package app.posato

import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.targets.data.LocalExactDomainPolicyStore
import app.posato.feature.targets.ui.ExactDomainsScreen
import dev.zacsweers.metro.Inject

@Inject
class PosatoApplication internal constructor(
    private val store: LocalExactDomainPolicyStore,
) {
    @Composable
    fun Content() {
        PosatoTheme {
            ExactDomainsScreen(store)
        }
    }
}
