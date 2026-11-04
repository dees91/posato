package app.posato.provisioning.store

import app.posato.provisioning.store.StoreFixtures.APPS
import app.posato.provisioning.store.StoreFixtures.ATTACHED_BUILD
import app.posato.provisioning.store.StoreFixtures.EMPTY
import app.posato.provisioning.store.StoreFixtures.list
import app.posato.provisioning.store.StoreFixtures.screenshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        "GET apps/APP/appInfos" to listOf(list("""{"id":"INFO","attributes":{"state":"WAITING_FOR_REVIEW"}}""")),
        "GET appInfos/INFO/appInfoLocalizations" to listOf(EMPTY),
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

    @Test
    fun `reports per locale which required listing fields are missing, including a required locale that is absent`() {
        val english = """{"id":"LOC","attributes":{"locale":"en-US","whatsNew":"New.","description":"Pause.",""" +
            """"keywords":"focus,pause","supportUrl":"https://posato.app/support/"}}"""
        val polish = """{"id":"PL","attributes":{"locale":"pl","whatsNew":"Translated notes.","description":"Translated description."}}"""
        val bothSets = """{"data":[{"id":"PHONE","attributes":{"screenshotDisplayType":"APP_IPHONE_67"}},""" +
            """{"id":"PAD","attributes":{"screenshotDisplayType":"APP_IPAD_PRO_3GEN_129"}}]}"""
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET apps/APP/appStoreVersions" to listOf(
                    list(
                        StoreFixtures.versionEntry("LIVE", "1.1.0", "READY_FOR_DISTRIBUTION"),
                        StoreFixtures.versionEntry("VER", "1.2.0", "PREPARE_FOR_SUBMISSION"),
                    ),
                ),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(list(english, polish)),
                "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to listOf(bothSets),
                "GET appStoreVersionLocalizations/PL/appScreenshotSets" to listOf(
                    """{"data":[{"id":"PHONE","attributes":{"screenshotDisplayType":"APP_IPHONE_67"}}]}""",
                ),
                "GET appScreenshotSets/PHONE/appScreenshots" to listOf(list(screenshot("A", "COMPLETE"))),
                "GET appScreenshotSets/PAD/appScreenshots" to listOf(list(screenshot("B", "COMPLETE"))),
                "GET apps/APP/appInfos" to listOf(
                    list(
                        """{"id":"LIVE-INFO","attributes":{"state":"READY_FOR_DISTRIBUTION"}}""",
                        """{"id":"INFO","attributes":{"state":"PREPARE_FOR_SUBMISSION"}}""",
                    ),
                ),
                "GET appInfos/INFO/appInfoLocalizations" to listOf(
                    list("""{"id":"I-EN","attributes":{"locale":"en-US","name":"Posato","privacyPolicyUrl":"https://posato.app/privacy/"}}"""),
                ),
            ),
        )

        val report = StoreStatus(harness.services).report("1.2.0", listOf("en-US", "pl", "de-DE"))

        assertTrue(harness.executor.writes.isEmpty())
        val liveInfoRead = harness.executor.requests.any { it.path.startsWith("appInfos/LIVE-INFO") }
        assertFalse(liveInfoRead, "The live App Information is not the one being submitted.")
        val selected = report["selected"]!!.jsonObject
        val byLocale = selected["localizations"]!!.jsonArray.associate { it.jsonObject.text("locale") to it.jsonObject }
        assertEquals(listOf("en-US", "pl", "de-DE"), byLocale.keys.toList())
        assertEquals("true", byLocale.getValue("en-US").text("complete"))
        assertEquals(emptyList(), missing(byLocale.getValue("en-US")))
        assertEquals(
            listOf("name", "keywords", "supportUrl", "privacyPolicyUrl", "screenshots APP_IPAD_PRO_3GEN_129"),
            missing(byLocale.getValue("pl")),
        )
        assertEquals("false", byLocale.getValue("de-DE").text("exists"))
        assertEquals("false", byLocale.getValue("de-DE").text("complete"))
        assertEquals("false", selected.text("localizationsComplete"))
    }

    @Test
    fun `requires no What's New on an app's first version`() {
        val first = """{"id":"LOC","attributes":{"locale":"en-US","description":"Pause.",""" +
            """"keywords":"focus","supportUrl":"https://posato.app/support/"}}"""
        val harness = StoreHarness(
            routes + mapOf(
                "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version()),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(list(first)),
                "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to listOf(EMPTY),
                "GET apps/APP/appInfos" to listOf(list("""{"id":"INFO","attributes":{"state":"PREPARE_FOR_SUBMISSION"}}""")),
                "GET appInfos/INFO/appInfoLocalizations" to listOf(
                    list("""{"id":"I-EN","attributes":{"locale":"en-US","name":"Posato","privacyPolicyUrl":"https://posato.app/privacy/"}}"""),
                ),
            ),
        )

        val localization = StoreStatus(harness.services).report("1.2.0")["selected"]!!.jsonObject["localizations"]!!.jsonArray.single().jsonObject

        assertEquals(listOf("screenshots APP_IPHONE_67", "screenshots APP_IPAD_PRO_3GEN_129"), missing(localization))
    }

    private fun missing(localization: JsonObject): List<String> = localization["missing"]!!.jsonArray.map { it.jsonPrimitive.content }
}
