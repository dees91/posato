package app.posato.feature.schedules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.onboarding.MacHelperSetupUiState
import app.posato.feature.onboarding.MacSetupAction
import app.posato.feature.onboarding.MacSetupOverview

internal fun MacHelperSetupUiState.schedulesSetupContent(): @Composable () -> Unit {
    return { SchedulesMacSetup(this) }
}

/** Schedules on a Mac need the same one setup, so the setup route runs it in place. */
@Composable
private fun SchedulesMacSetup(setup: MacHelperSetupUiState) {
    LaunchedEffect(setup) { setup.readQuietly() }
    val presentation = setup.presentation()
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        MacSetupOverview(run = presentation.setup)
        MacSetupAction(presentation, onSetUp = { setup.setUp(sessionBlocked = false) })
    }
}
