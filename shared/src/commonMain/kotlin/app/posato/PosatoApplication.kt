package app.posato

import androidx.compose.runtime.Composable
import app.posato.persistence.LocalExactDomainPolicyStore
import app.posato.ui.ExactDomainsScreen
import app.posato.ui.PosatoTheme
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
