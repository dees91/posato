package app.posato

import androidx.compose.runtime.Composable
import app.posato.ui.ApplicationShell
import dev.zacsweers.metro.Inject

@Inject
class PosatoApplication {
    @Composable
    fun Content() {
        ApplicationShell()
    }
}
