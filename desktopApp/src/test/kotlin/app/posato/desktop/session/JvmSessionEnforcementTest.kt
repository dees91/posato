package app.posato.desktop.session

import app.posato.feature.enforcement.ApplicationEnforcementLink
import app.posato.feature.enforcement.BrowserEnforcementLink
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.JvmSessionEnforcement
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmSessionEnforcementTest {
    @Test
    fun `given an empty request when applying then nothing is enforced without touching the links`() {
        val links = recordingLinks()
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)

        val report = runBlocking { enforcement.apply(request(emptyList(), emptyList())) }

        assertEquals(EnforcementOutcome.NOTHING_TO_ENFORCE, report.outcome)
        assertTrue(links.calls.isEmpty())
    }

    @Test
    fun `given healthy links when applying then the browser applies before the applications`() {
        val links = recordingLinks()
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)

        val report = runBlocking { enforcement.apply(request()) }

        assertEquals(EnforcementOutcome.APPLIED, report.outcome)
        assertEquals(listOf("browserStart", "appStart"), links.calls)
    }

    @Test
    fun `given an application failure when applying then the browser is restored and the apply fails`() {
        val links = recordingLinks(appStart = false)
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)

        val report = runBlocking { enforcement.apply(request()) }

        assertEquals(EnforcementOutcome.FAILED, report.outcome)
        assertEquals(listOf("browserStart", "appStart", "browserClear"), links.calls)
    }

    @Test
    fun `given a browser failure when applying then the applications stay untouched`() {
        val links = recordingLinks(browserStart = false)
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)

        val report = runBlocking { enforcement.apply(request()) }

        assertEquals(EnforcementOutcome.FAILED, report.outcome)
        assertEquals(listOf("browserStart"), links.calls)
    }

    @Test
    fun `given healthy links when clearing then both sides clear`() {
        val links = recordingLinks()
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)
        runBlocking { enforcement.apply(request()) }
        links.calls.clear()

        val outcome = runBlocking { enforcement.clear() }

        assertEquals(EnforcementOutcome.CLEARED, outcome)
        assertEquals(listOf("browserClear", "appClear"), links.calls)
    }

    @Test
    fun `given a failed restore when clearing then the outcome stays failed`() {
        val links = recordingLinks(browserClearOutcome = EnforcementOutcome.FAILED)
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)
        runBlocking { enforcement.apply(request()) }

        val outcome = runBlocking { enforcement.clear() }

        assertEquals(EnforcementOutcome.FAILED, outcome)
    }

    @Test
    fun `given an unregistered helper when clearing with nothing applied then the outcome is unavailable`() {
        val links = recordingLinks(
            browserClearOutcome = EnforcementOutcome.CLEARED,
            appClearOutcome = EnforcementOutcome.UNAVAILABLE,
        )
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)

        val outcome = runBlocking { enforcement.clear() }

        assertEquals(EnforcementOutcome.UNAVAILABLE, outcome)
    }

    @Test
    fun `given a missing helper when clearing with nothing applied then the outcome is unavailable`() {
        val links = recordingLinks(clearThrows = true)
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)

        val outcome = runBlocking { enforcement.clear() }

        assertEquals(EnforcementOutcome.UNAVAILABLE, outcome)
    }

    @Test
    fun `given a missing helper when clearing owned restrictions then the outcome stays failed`() {
        val links = recordingLinks(clearThrows = true)
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)
        runBlocking { enforcement.apply(request()) }

        val outcome = runBlocking { enforcement.clear() }

        assertEquals(EnforcementOutcome.FAILED, outcome)
    }

    @Test
    fun `given no applied session when reading status then the links stay untouched`() {
        val links = recordingLinks()
        val enforcement = JvmSessionEnforcement(links.browser, links.applications)

        val outcome = runBlocking { enforcement.status() }

        assertEquals(EnforcementOutcome.CLEARED, outcome)
        assertTrue(links.calls.isEmpty())
    }

    @Test
    fun `given an applied session when reading status then the helper answer decides`() {
        val applied = recordingLinks(applied = true)
        val appliedEnforcement = JvmSessionEnforcement(applied.browser, applied.applications)
        runBlocking { appliedEnforcement.apply(request()) }
        assertEquals(EnforcementOutcome.APPLIED, runBlocking { appliedEnforcement.status() })

        val lost = recordingLinks(applied = false)
        val lostEnforcement = JvmSessionEnforcement(lost.browser, lost.applications)
        runBlocking { lostEnforcement.apply(request()) }
        assertEquals(EnforcementOutcome.CLEARED, runBlocking { lostEnforcement.status() })

        val unknown = recordingLinks(applied = null)
        val unknownEnforcement = JvmSessionEnforcement(unknown.browser, unknown.applications)
        runBlocking { unknownEnforcement.apply(request()) }
        assertEquals(EnforcementOutcome.UNKNOWN, runBlocking { unknownEnforcement.status() })
    }

    @Test
    fun `given an applications only session when reading status then the helper service state decides`() {
        val ready = recordingLinks(serviceReady = true)
        val readyEnforcement = JvmSessionEnforcement(ready.browser, ready.applications)
        runBlocking { readyEnforcement.apply(request(emptyList())) }
        assertEquals(EnforcementOutcome.APPLIED, runBlocking { readyEnforcement.status() })

        val down = recordingLinks(serviceReady = false)
        val downEnforcement = JvmSessionEnforcement(down.browser, down.applications)
        runBlocking { downEnforcement.apply(request(emptyList())) }
        assertEquals(EnforcementOutcome.CLEARED, runBlocking { downEnforcement.status() })

        val unknown = recordingLinks(serviceReady = null)
        val unknownEnforcement = JvmSessionEnforcement(unknown.browser, unknown.applications)
        runBlocking { unknownEnforcement.apply(request(emptyList())) }
        assertEquals(EnforcementOutcome.UNKNOWN, runBlocking { unknownEnforcement.status() })
    }

    private fun request(
        domains: List<String> = listOf("stable.example"),
        mappingIds: List<String> = listOf("aa".repeat(32)),
    ): EnforcementRequest {
        return EnforcementRequest(
            domains,
            mappingIds,
            "session",
            1_000_000_000_000L,
            1_000_001_800_000L,
        )
    }

    private fun recordingLinks(
        browserStart: Boolean = true,
        browserClearOutcome: EnforcementOutcome = EnforcementOutcome.CLEARED,
        appStart: Boolean = true,
        appClearOutcome: EnforcementOutcome = EnforcementOutcome.CLEARED,
        clearThrows: Boolean = false,
        applied: Boolean? = true,
        serviceReady: Boolean? = true,
    ): RecordingLinks {
        val calls = ArrayDeque<String>()
        val browser = object : BrowserEnforcementLink {
            override fun start(
                domains: List<String>,
                sessionEndEpochMillis: Long,
            ): Boolean {
                calls.addLast("browserStart")
                return browserStart
            }

            override fun clear(): EnforcementOutcome {
                calls.addLast("browserClear")
                check(!clearThrows) { "helper is missing" }
                return browserClearOutcome
            }

            override fun isApplied(): Boolean? {
                calls.addLast("browserStatus")
                return applied
            }
        }
        val applications = object : ApplicationEnforcementLink {
            override suspend fun start(
                mappingIds: List<String>,
                sessionEndEpochMillis: Long,
            ): Boolean {
                calls.addLast("appStart")
                return appStart
            }

            override suspend fun clear(): EnforcementOutcome {
                calls.addLast("appClear")
                return appClearOutcome
            }

            override suspend fun isServiceReady(): Boolean? {
                calls.addLast("serviceStatus")
                return serviceReady
            }
        }
        return RecordingLinks(calls, browser, applications)
    }

    private class RecordingLinks(
        val calls: ArrayDeque<String>,
        val browser: BrowserEnforcementLink,
        val applications: ApplicationEnforcementLink,
    )
}
