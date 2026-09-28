package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.store.StoreFixtures.APPS
import app.posato.provisioning.store.StoreFixtures.EMPTY
import kotlinx.serialization.json.jsonObject
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoreWithdrawalTest {
    @Test
    fun `cancels the submission holding a waiting version and waits until the version is editable`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET apps/APP/appStoreVersions" to listOf(waiting, waiting, StoreFixtures.version(state = "DEVELOPER_REJECTED")),
                "GET reviewSubmissions" to listOf(submission("WAITING_FOR_REVIEW")),
                "GET reviewSubmissions/SUB/items" to listOf(ITEM_FOR_VERSION),
                "PATCH reviewSubmissions/SUB" to listOf("""{"data":{"id":"SUB","attributes":{"state":"CANCELING"}}}"""),
            ),
        )

        val result = StoreWithdrawal(harness.services).withdraw("1.2.0")

        assertEquals("canceled", result.text("submission"))
        assertEquals("DEVELOPER_REJECTED", result.text("state"))
        val cancel = harness.executor.write("PATCH reviewSubmissions/SUB").data()
        assertEquals("reviewSubmissions", cancel.text("type"))
        assertEquals("SUB", cancel.text("id"))
        val attributes = cancel["attributes"]!!.jsonObject
        assertEquals(setOf("canceled"), attributes.keys)
        assertEquals("true", attributes.text("canceled"))
        val lookup = harness.executor.requests.first { it.path == "reviewSubmissions" }
        assertTrue(lookup.query.contains("filter[state]" to "WAITING_FOR_REVIEW,IN_REVIEW,CANCELING"))
        assertEquals(List(2) { Duration.ofSeconds(5) }, harness.slept)
    }

    @Test
    fun `does nothing when the version is already editable`() {
        val harness = StoreHarness(
            mapOf("GET apps" to listOf(APPS), "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(state = "DEVELOPER_REJECTED"))),
        )

        val result = StoreWithdrawal(harness.services).withdraw("1.2.0")

        assertEquals("unchanged", result.text("submission"))
        assertEquals("DEVELOPER_REJECTED", result.text("state"))
        assertTrue(harness.executor.writes.isEmpty())
        assertTrue(harness.slept.isEmpty())
    }

    @Test
    fun `only waits when an earlier run left the submission canceling`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET apps/APP/appStoreVersions" to listOf(waiting, StoreFixtures.version(state = "PREPARE_FOR_SUBMISSION")),
                "GET reviewSubmissions" to listOf(submission("CANCELING")),
                "GET reviewSubmissions/SUB/items" to listOf(ITEM_FOR_VERSION),
            ),
        )

        val result = StoreWithdrawal(harness.services).withdraw("1.2.0")

        assertEquals("unchanged", result.text("submission"))
        assertEquals("PREPARE_FOR_SUBMISSION", result.text("state"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `fails as pending, after one cancellation, when the version stays in review past the poll bound`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET apps/APP/appStoreVersions" to listOf(waiting),
                "GET reviewSubmissions" to listOf(submission("WAITING_FOR_REVIEW")),
                "GET reviewSubmissions/SUB/items" to listOf(ITEM_FOR_VERSION),
                "PATCH reviewSubmissions/SUB" to listOf("""{"data":{"id":"SUB","attributes":{"state":"CANCELING"}}}"""),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreWithdrawal(harness.services).withdraw("1.2.0") }

        assertEquals(ErrorCode.WITHDRAWAL_PENDING, failure.code)
        assertTrue(failure.hint.orEmpty().contains("Rerun `store withdraw --version 1.2.0`"))
        assertEquals(1, harness.executor.writes.size)
        assertEquals(Duration.ofMinutes(2), harness.slept.fold(Duration.ZERO, Duration::plus))
    }

    @Test
    fun `refuses a submission that also holds an App Event, with no write`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET apps/APP/appStoreVersions" to listOf(waiting),
                "GET reviewSubmissions" to listOf(submission("WAITING_FOR_REVIEW")),
                "GET reviewSubmissions/SUB/items" to listOf(
                    """{"data":[{"id":"ITEM","relationships":{"appStoreVersion":{"data":{"type":"appStoreVersions","id":"VER"}}}},""" +
                        """{"id":"EVENT-ITEM","relationships":{"appEvent":{"data":{"type":"appEvents","id":"EVENT"}}}}]}""",
                ),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreWithdrawal(harness.services).withdraw("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_NOT_READY, failure.code)
        assertTrue(failure.message.orEmpty().contains("other than this version"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses a waiting version that no submission holds, with no write`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET apps/APP/appStoreVersions" to listOf(waiting),
                "GET reviewSubmissions" to listOf(EMPTY),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreWithdrawal(harness.services).withdraw("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_MISSING, failure.code)
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses a version past review before looking for a submission`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(state = "PENDING_DEVELOPER_RELEASE")),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreWithdrawal(harness.services).withdraw("1.2.0") }

        assertEquals(ErrorCode.VERSION_NOT_EDITABLE, failure.code)
        assertTrue(failure.message.orEmpty().contains("PENDING_DEVELOPER_RELEASE"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    private val waiting = StoreFixtures.version(state = "WAITING_FOR_REVIEW")

    private fun submission(state: String): String = """{"data":[{"id":"SUB","attributes":{"state":"$state"}}]}"""

    private companion object {
        const val ITEM_FOR_VERSION = """{"data":[{"id":"ITEM","relationships":{"appStoreVersion":""" +
            """{"data":{"type":"appStoreVersions","id":"VER"}}}}]}"""
    }
}
