package app.posato.feature.schedules.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoLayout
import app.posato.feature.notifications.NotificationPermission
import app.posato.feature.notifications.SessionNotifier
import app.posato.feature.onboarding.MacHelperSetupUiState
import app.posato.feature.onboarding.promptInProgress
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.domain.RandomScheduleIdGenerator
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.targets.ui.loadPauseSetRows
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@Composable
internal fun SchedulesDestination(
    inputs: SchedulesInputs,
    device: PosatoDevice,
    layout: PosatoLayout,
    macSetupState: MacHelperSetupUiState?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val holder = remember(inputs) {
        SchedulesHolder(
            inputs.store,
            inputs.zone,
            inputs.clock,
            inputs.timeFormat,
            RandomScheduleIdGenerator,
            scope,
            linked = { inputs.sync.state.value.linked },
            onSaved = { inputs.notifier?.onScheduleSaved() },
            pauseSets = inputs.pauseSetSource(device.noun),
        )
    }
    LaunchedEffect(macSetupState) { macSetupState?.readQuietly() }
    var screenTimeAllowed by remember(inputs) { mutableStateOf(true) }
    LaunchedEffect(inputs) {
        inputs.applicationMappings.invalidations.onStart { emit(Unit) }.collect {
            screenTimeAllowed = inputs.applicationMappings.load().allowsScheduledStarts()
        }
    }
    val notices = inputs.notifier?.settings?.collectAsState()?.value
    val readiness = ScheduleDeviceReadiness(
        mac = macSetupState?.presentation()?.scheduleReadiness() ?: MacScheduleReadiness.READY,
        screenTimeAllowed = screenTimeAllowed,
        offerNotices = notices?.enabled == true && notices.permission == NotificationPermission.NOT_DETERMINED,
    )
    SchedulesScreen(
        holder = holder,
        device = device,
        layout = layout,
        readiness = readiness,
        modifier = modifier,
        onAllowScreenTime = {
            scope.launch {
                inputs.applicationAccess.requestAuthorization()
                screenTimeAllowed = inputs.applicationMappings.load().allowsScheduledStarts()
            }
        },
        onTurnOnNotices = { scope.launch { inputs.notifier?.askNow() } },
        onAllowSchedules = { macSetupState?.let { it.consent.allow(it.presentation()) } },
        macSetupContent = macSetupState?.schedulesSetupContent(),
        setupPromptOpen = macSetupState?.presentation()?.promptInProgress() == true,
    )
}

private fun SchedulesInputs.pauseSetSource(deviceNoun: String): PauseSetSource {
    val policies = policies ?: return PauseSetSource(deviceNoun = deviceNoun)
    return PauseSetSource(merge(policies.policyChanges, applicationMappings.invalidations), deviceNoun) {
        loadPauseSetRows(policies, applicationMappings)
    }
}
