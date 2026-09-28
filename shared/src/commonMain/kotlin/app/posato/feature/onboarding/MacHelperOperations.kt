package app.posato.feature.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts the helper operations a person started that may ask for approval or change what the helper
 * holds (enable, check, remove, the grant switch), so an automatic start never runs beside one.
 */
public class MacHelperOperations {
    private val running = MutableStateFlow(0)
    public val inFlight: StateFlow<Int> = running.asStateFlow()

    public suspend fun <T> track(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }
}
