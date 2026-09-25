package app.posato.provisioning.store

import app.posato.provisioning.store.StoreFixtures.APPS
import app.posato.provisioning.store.StoreFixtures.ATTACHED_BUILD
import app.posato.provisioning.store.StoreFixtures.list
import app.posato.provisioning.store.StoreFixtures.screenshot
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreStatusTest {
    private val routes = mapOf(
        "GET apps" to listOf(APPS),
        "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(state = "WAITING_FOR_REVIEW")),
        "GET builds" to listOf(
            """{"data":[{"id":"B5","attributes":{"version":"5","processingState":"VALID"},""" +
                """"relationships":{"preReleaseVersion":{"data":{"type":"preReleaseVersions","id":"PRV"}}}},""" +
                """{"id":"B4","attributes":{"version":"4","processingState":"VALID"}}],""" +
                """"included":[{"type":"preReleaseVersions","id":"PRV","attributes":{"version":"1.2.0"}}],""" +
                """"links":{"next":"https://api.appstoreconnect.apple.com/v1/builds?cursor=abc"}}""",
        ),
        "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD),
        "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("New things.")),
        "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to listOf(
            """{"data":[{"id":"SET","attributes":{"screenshotDisplayType":"APP_IPHONE_67"}}]}""",
        ),
        "GET appScreenshotSets/SET/appScreenshots" to listOf(list(screenshot("A", "COMPLETE"), screenshot("B", "COMPLETE"))),
    )

    @Test
    fun `reports versions, builds, the next build number, and the selected version without writing`() {
        val harness = StoreHarness(routes)

        val report = StoreStatus(harness.services).report("1.2.0")

        assertTrue(harness.executor.writes.isEmpty())
        assertEquals("6", report.text("nextBuildNumber"))
        assertEquals("true", report.text("moreBuilds"), "A newest-first page that has more is reported, not followed.")
        assertEquals(1, harness.executor.requests.count { it.path == "builds" })
        val builds = report["builds"]!!.jsonArray
        assertEquals("1.2.0", builds[0].jsonObject.text("version"))
        val selected = report["selected"]!!.jsonObject
        assertEquals("WAITING_FOR_REVIEW", selected.text("state"))
        assertEquals("New things.", selected.text("whatsNew"))
        assertEquals("5", selected["build"]!!.jsonObject.text("build"))
        val set = selected["screenshotSets"]!!.jsonArray.single().jsonObject
        assertEquals("2", set.text("count"))
        assertEquals("2", set["deliveryStates"]!!.jsonObject.text("COMPLETE"))
    }

    @Test
    fun `reports a version App Store Connect does not hold yet`() {
        val report = StoreStatus(StoreHarness(routes).services).report("9.9.9")

        assertEquals("false", report["selected"]!!.jsonObject.text("exists"))
        assertFalse(report.toString().contains("\"VER\""), "The report carries no resource identifiers.")
    }
}
