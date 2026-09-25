package app.posato.feature.about

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

@Immutable
public data class ApplicationUpdatesState(
    val available: Boolean = false,
    val automaticChecks: Boolean = false,
    val canCheckNow: Boolean = false,
)

@Stable
public interface ApplicationUpdates {
    public val state: StateFlow<ApplicationUpdatesState>

    public fun setAutomaticChecks(enabled: Boolean)

    public fun checkNow()

    public fun askForAutomaticChecksOnce()
}
