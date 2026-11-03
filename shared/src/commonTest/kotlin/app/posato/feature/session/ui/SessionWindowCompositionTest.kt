package app.posato.feature.session.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.DomainWindowPlan
import app.posato.feature.enforcement.EnforcedSet
import app.posato.feature.enforcement.PauseItems
import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.enforcement.blockedNow
import app.posato.feature.enforcement.windowSegment
import app.posato.feature.schedules.domain.OffsetScheduleZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.utc
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import app.posato.feature.targets.domain.WebsiteWindow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A manual session with window rules, with an injected clock. Failure modes: a ruled website stays blocked or an
 * unruled one opens, an open window frees room or changes what is shown as not paused yet, and a rule edited
 * during the session never re-applies it.
 */
class SessionWindowCompositionTest {
    private val zone = OffsetScheduleZone { 0L }
    private val start = utc(ScheduleDate(2026, 11, 2), 10 * 60)
    private val end = start + 60 * MINUTE
    private val record = SessionRecord(SessionId(testIdentifier(21)), start, end)
    private val halfHourly = checkNotNull(WebsiteWindow.of(30, 5))
    private val hourly = checkNotNull(WebsiteWindow.of(60, 10))

    private var setDomains: List<String> = emptyList()
    private var setWindows: Map<String, WebsiteWindow> = emptyMap()

    private fun targets(): SessionTargetsState {
        val policy = (TargetPolicy.fromStoredValues(setDomains, null) as TargetPolicyValidationResult.Success).policy
        val windows = setWindows.mapKeys { (domain, _) -> checkNotNull(ExactDomain.restore(domain)) }
        val sets = checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, null, policy.domains, windows = windows)), null))
        val mappings = LocalApplicationMappingsLoadResult.Success(
            checkNotNull(LocalApplicationMappingsSnapshot.restore(emptyList())),
            LocalApplicationMappingsAccess.READY,
        )
        return SessionTargetsState(policy, mappings, PauseSetId.FIRST, sets)
    }

    private fun SessionTargetsState.request() = record.toEnforcementRequest(
        EnforcedSet(policy?.domains.orEmpty().map(ExactDomain::canonicalValue).toPersistentList(), 0),
        this,
    )

    @Test
    fun `given a ruled and an unruled website when the session is composed then only the ruled one opens inside its window`() = runTest {
        withComposition("session-window-manual.db") { composition ->
            setDomains = listOf("mail.example", "news.example")
            setWindows = mapOf("news.example" to halfHourly)

            val request = composition.compose(record).request()

            assertEquals(listOf("mail.example", "news.example"), request.domains)
            assertEquals(listOf(DomainWindowPlan("news.example", listOf(windowSegment(end, halfHourly)))), request.windows)
            assertEquals(listOf("mail.example", "news.example"), request.blockedNow(start + 30 * MINUTE - 1, zone))
            assertEquals(listOf("mail.example"), request.blockedNow(start + 30 * MINUTE, zone))
            assertEquals(listOf("mail.example", "news.example"), request.blockedNow(start + 35 * MINUTE, zone))
        }
    }

    @Test
    fun `given every paused website ruled when it is open then nothing is blocked yet the request still holds every website`() = runTest {
        withComposition("session-window-all-open.db") { composition ->
            setDomains = listOf("news.example")
            setWindows = mapOf("news.example" to halfHourly)

            val request = composition.compose(record).request()

            assertEquals(listOf("news.example"), request.domains, "an all-open interval is still a pause to enforce")
            assertEquals(emptyList(), request.blockedNow(start + 31 * MINUTE, zone))
            assertEquals(listOf("news.example"), request.blockedNow(start + 35 * MINUTE, zone), "blocking returns at the window end")
        }
    }

    @Test
    fun `given a ruled website that left the set when the session is composed again then it stays paused with no window`() = runTest {
        withComposition("session-window-retained.db") { composition ->
            setDomains = listOf("mail.example", "news.example")
            setWindows = mapOf("news.example" to halfHourly)
            composition.compose(record)

            setDomains = listOf("mail.example")
            setWindows = emptyMap()
            val request = composition.compose(record).request()

            assertEquals(listOf("mail.example", "news.example"), request.domains)
            assertEquals(emptyList(), request.windows)
        }
    }

    @Test
    fun `given a running session when a rule is added edited or removed then each change applies the session again`() = runTest {
        withComposition("session-window-edits.db") { composition ->
            setDomains = listOf("news.example")
            composition.compose(record)

            setWindows = mapOf("news.example" to halfHourly)
            assertTrue(composition.differs(record), "added")
            setWindows = mapOf("news.example" to hourly)
            assertTrue(composition.differs(record), "edited")
            assertEquals(listOf(windowSegment(end, hourly)), composition.compose(record).windows.single().segments)
            setWindows = emptyMap()
            assertTrue(composition.differs(record), "removed")
            assertEquals(emptyList(), composition.compose(record).windows)
            assertFalse(composition.differs(record), "unchanged")
        }
    }

    @Test
    fun `given a limit that leaves a website waiting when a rule opens another then no room is freed and the count is unchanged`() = runTest {
        withComposition("session-window-limits.db") { composition ->
            // A schedule already pauses all but one of the Mac's websites, so only one of the set's fits.
            composition.occupied = { PauseItems((1 until 1_024).mapTo(mutableSetOf()) { index -> "busy$index.example" }) }
            setDomains = listOf("a.example", "b.example")
            setWindows = mapOf("a.example" to halfHourly)

            val composed = composition.compose(record)

            assertEquals(listOf("a.example"), composed.policy?.domains?.map(ExactDomain::canonicalValue))
            assertEquals(1, composition.notPausedYet.value)
            assertEquals(listOf("a.example"), composed.windows.map(DomainWindowPlan::domain))
        }
    }

    private suspend fun withComposition(
        name: String,
        limits: PauseLimits = PauseLimits.MAC,
        block: suspend (SessionComposition) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            SqlLocalSessionStore(database, Dispatchers.Default)
                .start(record.sessionId, start, end, start, FrozenStartSet(persistentListOf(), 0))
            block(SessionComposition(SqlPartRetentionStore(database, Dispatchers.Default), limits, { targets() }))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val MINUTE: Long = 60_000L
    }
}
