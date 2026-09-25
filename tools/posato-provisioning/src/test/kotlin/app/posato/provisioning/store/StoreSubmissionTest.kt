package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.store.StoreFixtures.APPS
import app.posato.provisioning.store.StoreFixtures.ATTACHED_BUILD
import app.posato.provisioning.store.StoreFixtures.EMPTY
import app.posato.provisioning.store.StoreFixtures.NO_BUILD
import app.posato.provisioning.store.StoreFixtures.list
import app.posato.provisioning.store.StoreFixtures.screenshot
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoreSubmissionTest {
    @Test
    fun `creates a submission, adds the version, and submits it`() {
        val harness = StoreHarness(
            ready(
                "GET reviewSubmissions" to EMPTY,
                "POST reviewSubmissions" to """{"data":{"id":"SUB","attributes":{"state":"READY_FOR_REVIEW"}}}""",
                "POST reviewSubmissionItems" to """{"data":{"id":"ITEM"}}""",
                "PATCH reviewSubmissions/SUB" to """{"data":{"id":"SUB","attributes":{"state":"WAITING_FOR_REVIEW"}}}""",
            ),
        )

        val result = StoreSubmission(harness.services).submit("1.2.0")

        assertEquals("submitted", result.text("submission"))
        assertEquals("WAITING_FOR_REVIEW", result.text("state"))
        assertEquals(
            listOf("POST reviewSubmissions", "POST reviewSubmissionItems", "PATCH reviewSubmissions/SUB"),
            harness.executor.writes.map { "${it.method} ${it.path}" },
        )
        val lookup = harness.executor.requests.first { it.path == "reviewSubmissions" }
        assertTrue(lookup.query.contains("filter[state]" to "READY_FOR_REVIEW,WAITING_FOR_REVIEW,IN_REVIEW,UNRESOLVED_ISSUES"))
        assertTrue(lookup.query.contains("filter[platform]" to "IOS"))

        val create = harness.executor.write("POST reviewSubmissions").data()
        assertEquals("IOS", create["attributes"]!!.jsonObject.text("platform"))
        assertEquals("APP", create["relationships"]!!.jsonObject["app"]!!.jsonObject["data"]!!.jsonObject.text("id"))
        val item = harness.executor.write("POST reviewSubmissionItems").data()["relationships"]!!.jsonObject
        assertEquals("SUB", item["reviewSubmission"]!!.jsonObject["data"]!!.jsonObject.text("id"))
        assertEquals("VER", item["appStoreVersion"]!!.jsonObject["data"]!!.jsonObject.text("id"))
        val submit = harness.executor.write("PATCH reviewSubmissions/SUB").data()
        assertEquals("true", submit["attributes"]!!.jsonObject.text("submitted"))
    }

    @Test
    fun `does nothing when the version is already waiting for review`() {
        val harness = StoreHarness(
            ready(
                "GET reviewSubmissions" to """{"data":[{"id":"SUB","attributes":{"state":"WAITING_FOR_REVIEW"}}]}""",
                "GET reviewSubmissions/SUB/items" to ITEM_FOR_VERSION,
            ),
        )

        val result = StoreSubmission(harness.services).submit("1.2.0")

        assertEquals("already-submitted", result.text("submission"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `finishes an unsubmitted draft rather than creating a second submission`() {
        val harness = StoreHarness(
            ready(
                "GET reviewSubmissions" to """{"data":[{"id":"SUB","attributes":{"state":"READY_FOR_REVIEW"}}]}""",
                "GET reviewSubmissions/SUB/items" to ITEM_FOR_VERSION,
                "PATCH reviewSubmissions/SUB" to """{"data":{"id":"SUB","attributes":{"state":"WAITING_FOR_REVIEW"}}}""",
            ),
        )

        StoreSubmission(harness.services).submit("1.2.0")

        assertEquals(listOf("PATCH reviewSubmissions/SUB"), harness.executor.writes.map { "${it.method} ${it.path}" })
    }

    @Test
    fun `refuses a version without a build`() {
        val harness = StoreHarness(ready("GET reviewSubmissions" to EMPTY, "GET appStoreVersions/VER/build" to NO_BUILD))

        val failure = assertFailsWith<ProvisioningException> { StoreSubmission(harness.services).submit("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_NOT_READY, failure.code)
        assertTrue(failure.message.orEmpty().contains("no build"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses a version whose screenshots are still processing`() {
        val harness = StoreHarness(
            ready(
                "GET reviewSubmissions" to EMPTY,
                "GET appScreenshotSets/SET/appScreenshots" to list(screenshot("A", "COMPLETE"), screenshot("B", "UPLOAD_COMPLETE")),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreSubmission(harness.services).submit("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_NOT_READY, failure.code)
        assertTrue(failure.message.orEmpty().contains("1 screenshot(s) are not COMPLETE"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `resubmits the unresolved submission that holds a rejected version`() {
        val harness = StoreHarness(
            ready(
                "GET apps/APP/appStoreVersions" to StoreFixtures.version(state = "REJECTED"),
                "GET reviewSubmissions" to """{"data":[{"id":"SUB","attributes":{"state":"UNRESOLVED_ISSUES"}}]}""",
                "GET reviewSubmissions/SUB/items" to ITEM_FOR_VERSION,
                "PATCH reviewSubmissions/SUB" to """{"data":{"id":"SUB","attributes":{"state":"WAITING_FOR_REVIEW"}}}""",
            ),
        )

        val result = StoreSubmission(harness.services).submit("1.2.0")

        assertEquals("resubmitted", result.text("submission"))
        assertEquals(listOf("PATCH reviewSubmissions/SUB"), harness.executor.writes.map { "${it.method} ${it.path}" })
        assertEquals("true", harness.executor.writes.single().data()["attributes"]!!.jsonObject.text("submitted"))
    }

    @Test
    fun `marks a rejected item resolved before resubmitting its submission`() {
        val harness = StoreHarness(
            ready(
                "GET apps/APP/appStoreVersions" to StoreFixtures.version(state = "REJECTED"),
                "GET reviewSubmissions" to """{"data":[{"id":"SUB","attributes":{"state":"UNRESOLVED_ISSUES"}}]}""",
                "GET reviewSubmissions/SUB/items" to """{"data":[{"id":"ITEM","attributes":{"state":"REJECTED","resolved":false},""" +
                    """"relationships":{"appStoreVersion":{"data":{"type":"appStoreVersions","id":"VER"}}}}]}""",
                "PATCH reviewSubmissionItems/ITEM" to """{"data":{"id":"ITEM","attributes":{"resolved":true}}}""",
                "PATCH reviewSubmissions/SUB" to """{"data":{"id":"SUB","attributes":{"state":"WAITING_FOR_REVIEW"}}}""",
            ),
        )

        StoreSubmission(harness.services).submit("1.2.0")

        assertEquals(
            listOf("PATCH reviewSubmissionItems/ITEM", "PATCH reviewSubmissions/SUB"),
            harness.executor.writes.map { "${it.method} ${it.path}" },
        )
        val resolve = harness.executor.writes.first().data()
        assertEquals("reviewSubmissionItems", resolve.text("type"))
        assertEquals("ITEM", resolve.text("id"))
        assertEquals("true", resolve["attributes"]!!.jsonObject.text("resolved"))
    }

    @Test
    fun `refuses a draft that also holds an App Event, with no write`() {
        val harness = StoreHarness(
            ready(
                "GET reviewSubmissions" to """{"data":[{"id":"SUB","attributes":{"state":"READY_FOR_REVIEW"}}]}""",
                "GET reviewSubmissions/SUB/items" to """{"data":[{"id":"EVENT-ITEM","attributes":{"state":"READY_FOR_REVIEW"},""" +
                    """"relationships":{"appStoreVersion":{"data":null},"appEvent":{"data":{"type":"appEvents","id":"EVENT"}}}}]}""",
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreSubmission(harness.services).submit("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_NOT_READY, failure.code)
        assertTrue(failure.message.orEmpty().contains("other than this version"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses an unresolved submission that holds the version and an App Event, with no write`() {
        val harness = StoreHarness(
            ready(
                "GET apps/APP/appStoreVersions" to StoreFixtures.version(state = "REJECTED"),
                "GET reviewSubmissions" to """{"data":[{"id":"SUB","attributes":{"state":"UNRESOLVED_ISSUES"}}]}""",
                "GET reviewSubmissions/SUB/items" to """{"data":[{"id":"ITEM","relationships":{"appStoreVersion":""" +
                    """{"data":{"type":"appStoreVersions","id":"VER"}}}},{"id":"EVENT-ITEM","relationships":{"appEvent":""" +
                    """{"data":{"type":"appEvents","id":"EVENT"}}}}]}""",
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreSubmission(harness.services).submit("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_NOT_READY, failure.code)
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses to create a second submission while an unresolved one holds another item`() {
        val harness = StoreHarness(
            ready(
                "GET apps/APP/appStoreVersions" to StoreFixtures.version(state = "REJECTED"),
                "GET reviewSubmissions" to """{"data":[{"id":"SUB","attributes":{"state":"UNRESOLVED_ISSUES"}}]}""",
                "GET reviewSubmissions/SUB/items" to """{"data":[{"id":"ITEM","relationships":{"appStoreVersion":""" +
                    """{"data":{"type":"appStoreVersions","id":"OLDER"}}}}]}""",
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreSubmission(harness.services).submit("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_NOT_READY, failure.code)
        assertTrue(failure.message.orEmpty().contains("unresolved issues"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses a released version before creating a submission`() {
        val harness = StoreHarness(
            ready(
                "GET reviewSubmissions" to EMPTY,
                "GET apps/APP/appStoreVersions" to StoreFixtures.version(state = "READY_FOR_DISTRIBUTION"),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StoreSubmission(harness.services).submit("1.2.0") }

        assertEquals(ErrorCode.SUBMISSION_NOT_READY, failure.code)
        assertTrue(failure.message.orEmpty().contains("READY_FOR_DISTRIBUTION"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses a version App Store Connect does not hold`() {
        val harness = StoreHarness(mapOf("GET apps" to listOf(APPS), "GET apps/APP/appStoreVersions" to listOf(EMPTY)))

        val failure = assertFailsWith<ProvisioningException> { StoreSubmission(harness.services).submit("1.2.0") }

        assertEquals(ErrorCode.VERSION_MISSING, failure.code)
    }

    /** An account whose version 1.2.0 has a build and one delivered screenshot; [overrides] replace single routes. */
    private fun ready(vararg overrides: Pair<String, String>): Map<String, List<String>> {
        val routes = mutableMapOf(
            "GET apps" to APPS,
            "GET apps/APP/appStoreVersions" to StoreFixtures.version(),
            "GET appStoreVersions/VER/build" to ATTACHED_BUILD,
            "GET appStoreVersions/VER/appStoreVersionLocalizations" to StoreFixtures.localizations("New things."),
            "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to
                """{"data":[{"id":"SET","attributes":{"screenshotDisplayType":"APP_IPHONE_67"}}]}""",
            "GET appScreenshotSets/SET/appScreenshots" to list(screenshot("A", "COMPLETE")),
        )
        routes.putAll(overrides)
        return routes.mapValues { (_, body) -> listOf(body) }
    }

    private companion object {
        const val ITEM_FOR_VERSION = """{"data":[{"id":"ITEM","relationships":{"appStoreVersion":""" +
            """{"data":{"type":"appStoreVersions","id":"VER"}}}}]}"""
    }
}
