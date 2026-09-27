package app.posato.feature.schedules.ui

import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleScreenTimeReadinessTest {
    @Test
    fun `given each Screen Time answer then only a known missing authorization asks for it`() {
        val empty = LocalApplicationMappingsSnapshot.empty()
        val answers = LocalApplicationMappingsAccess.entries.associateWith {
            LocalApplicationMappingsLoadResult.Success(empty, it).allowsScheduledStarts()
        }

        assertEquals(
            mapOf(
                LocalApplicationMappingsAccess.READY to true,
                LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED to false,
                LocalApplicationMappingsAccess.AUTHORIZATION_DENIED to false,
                LocalApplicationMappingsAccess.RESTRICTED to false,
            ),
            answers,
        )
        assertEquals(true, LocalApplicationMappingsLoadResult.Unavailable().allowsScheduledStarts())
        assertEquals(true, LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE).allowsScheduledStarts())
    }
}

class MacScheduleReadinessTest {
    @Test
    fun `given each Mac setup state then Schedules asks for the one thing missing and nothing before the first read`() {
        val ready = app.posato.feature.onboarding.MacHelperReadiness.READY
        val cases = mapOf(
            app.posato.feature.onboarding.MacSetupPresentation() to MacScheduleReadiness.UNKNOWN,
            app.posato.feature.onboarding.MacSetupPresentation(readiness = ready) to MacScheduleReadiness.SETUP,
            app.posato.feature.onboarding.MacSetupPresentation(readiness = ready, setupComplete = true, schedulesNeedConsent = true) to
                MacScheduleReadiness.CONSENT,
            app.posato.feature.onboarding.MacSetupPresentation(readiness = ready, setupComplete = true, readyForSchedules = true) to
                MacScheduleReadiness.READY,
        )

        cases.forEach { (presentation, expected) -> assertEquals(expected, presentation.scheduleReadiness()) }
    }
}
