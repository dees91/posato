package app.posato.feature.enforcement

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertFalse

class EnforcementRedactionTest {
    @Test
    fun `given enforcement carriers when stringified then no domain or identifier leaks`() {
        val request = EnforcementRequest(
            listOf("stable.example"),
            listOf("a".repeat(64)),
            "session-9",
            1_000_000_000_000L,
            1_000_001_800_000L,
        )
        val report = EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
        val enforced = EnforcedSet(persistentListOf("stable.example"), 1)
        val active = EnforcementState.Active(false)
        val required = EnforcementState.ActionRequired(EnforcementActionKind.APPLY_FAILED, true)

        val rendered = listOf(request, report, enforced, active, required).joinToString { carrier -> carrier.toString() }

        assertFalse(rendered.contains("stable.example"))
        assertFalse(rendered.contains("a".repeat(64)))
        assertFalse(rendered.contains("session-9"))
    }
}
