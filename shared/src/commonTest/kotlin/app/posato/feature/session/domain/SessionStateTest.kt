package app.posato.feature.session.domain

import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionStateTest {
    @Test
    fun `given no stored session when evaluated then there is no session`() {
        val evaluation = SessionState.evaluate(null, false, false, NOW)

        assertEquals(SessionEvaluation.NoSession, evaluation)
    }

    @Test
    fun `given a stored session before its end when evaluated then it is active with the remaining time`() {
        val evaluation = SessionState.evaluate(record(END), false, false, NOW)

        val active = assertIs<SessionEvaluation.ShowActive>(evaluation)
        assertEquals(END - NOW, active.remainingMillis)
    }

    @Test
    fun `given a stored session exactly at its end when evaluated then expiry must be committed`() {
        val evaluation = SessionState.evaluate(record(END), false, false, END)

        assertIs<SessionEvaluation.CommitExpiry>(evaluation)
    }

    @Test
    fun `given a stored session past its end when evaluated then expiry must be committed`() {
        val evaluation = SessionState.evaluate(record(END), false, false, END + 1_000L)

        assertIs<SessionEvaluation.CommitExpiry>(evaluation)
    }

    @Test
    fun `given an early ended session past its end when evaluated then it stays ended early`() {
        val evaluation = SessionState.evaluate(record(END), true, false, END + 60_000L)

        val ended = assertIs<SessionEvaluation.ShowEnded>(evaluation)
        assertEquals(SessionEndKind.ENDED_EARLY, ended.kind)
    }

    @Test
    fun `given a marked session before its end when evaluated then it stays expired`() {
        val evaluation = SessionState.evaluate(record(END), false, true, NOW)

        val ended = assertIs<SessionEvaluation.ShowEnded>(evaluation)
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
    }

    @Test
    fun `given a marked session after a wall clock rollback when evaluated then it stays expired`() {
        val evaluation = SessionState.evaluate(record(END), false, true, START - 60_000L)

        val ended = assertIs<SessionEvaluation.ShowEnded>(evaluation)
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
        const val START: Long = NOW - 60_000L
        const val END: Long = NOW + 30 * 60_000L

        fun record(end: Long): SessionRecord {
            return SessionRecord(SessionId(testIdentifier(7)), START, end)
        }
    }
}
