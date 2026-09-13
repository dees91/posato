package app.posato.feature.enforcement

import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionEnforcementIdsTest {
    @Test
    fun `given a session id when round-tripped then the reconciliation id parses back`() {
        val original = SessionId(testIdentifier(130))

        assertEquals(original, sessionIdFromReconciliationId(original.reconciliationId()))
    }

    @Test
    fun `given a malformed reconciliation id when parsed then absent is returned`() {
        assertNull(sessionIdFromReconciliationId(""))
        assertNull(sessionIdFromReconciliationId("not-hex-at-all-00000000000000000000"))
        assertNull(sessionIdFromReconciliationId("0".repeat(31)))
        assertNull(sessionIdFromReconciliationId("0".repeat(33)))
        assertNull(sessionIdFromReconciliationId("zz".repeat(16)))
    }
}
