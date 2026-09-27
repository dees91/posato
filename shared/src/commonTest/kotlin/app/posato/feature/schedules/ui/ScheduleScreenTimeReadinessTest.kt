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
