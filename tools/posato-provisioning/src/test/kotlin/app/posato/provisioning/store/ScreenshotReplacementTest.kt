package app.posato.provisioning.store

import app.posato.provisioning.asc.HttpMethod
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.store.StoreFixtures.list
import app.posato.provisioning.store.StoreFixtures.screenshot
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val UPLOAD_URL = "https://store-uploads.example.apple.com/part?signature=SECRET"

class ScreenshotReplacementTest {
    private val first = ScreenshotFile("01-paused.png", "first-image".toByteArray())
    private val second = ScreenshotFile("02-session.png", "second-image-bytes".toByteArray())

    @Test
    fun `deletes the old set, uploads each file in order, and commits it with its MD5`() {
        val harness = StoreHarness(
            mapOf(
                "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to listOf(
                    """{"data":[{"id":"SET","attributes":{"screenshotDisplayType":"APP_IPHONE_67"}}]}""",
                ),
                "GET appScreenshotSets/SET/appScreenshots" to listOf(list(screenshot("OLD", "COMPLETE"))),
                "DELETE appScreenshots/OLD" to listOf(""),
                "POST appScreenshots" to listOf(reservation("NEW1", first), reservation("NEW2", second)),
                "PATCH appScreenshots/NEW1" to listOf("""{"data":{"id":"NEW1"}}"""),
                "PATCH appScreenshots/NEW2" to listOf("""{"data":{"id":"NEW2"}}"""),
            ),
        )

        val outcome = harness.services.replacement.replace("LOC", ScreenshotSlot.IPHONE, listOf(first, second))

        assertEquals(SetReplacement("SET", SetOutcome.REPLACED), outcome)
        assertEquals(
            listOf(
                "DELETE appScreenshots/OLD",
                "POST appScreenshots",
                "PATCH appScreenshots/NEW1",
                "POST appScreenshots",
                "PATCH appScreenshots/NEW2",
            ),
            harness.executor.writes.map { "${it.method} ${it.path}" },
        )
        val reserve = harness.executor.writes[1].data()
        assertEquals("01-paused.png", reserve["attributes"]!!.jsonObject.text("fileName"))
        assertEquals(first.bytes.size.toString(), reserve["attributes"]!!.jsonObject.text("fileSize"))
        assertEquals("SET", reserve["relationships"]!!.jsonObject["appScreenshotSet"]!!.jsonObject["data"]!!.jsonObject.text("id"))
        val commit = harness.executor.writes[2].data()["attributes"]!!.jsonObject
        assertEquals("true", commit.text("uploaded"))
        assertEquals(ReleaseInputs.md5(first.bytes), commit.text("sourceFileChecksum"))
        assertEquals(2, harness.transport.parts.size)
        assertContentEquals(first.bytes, harness.transport.parts[0].second)
        assertEquals(listOf("Content-Type" to "image/png"), harness.transport.parts[0].first.headers)
    }

    @Test
    fun `creates the set when the version has none for that display type`() {
        val harness = StoreHarness(
            mapOf(
                "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to listOf(StoreFixtures.EMPTY),
                "POST appScreenshotSets" to listOf("""{"data":{"id":"PAD","attributes":{"screenshotDisplayType":"APP_IPAD_PRO_3GEN_129"}}}"""),
                "GET appScreenshotSets/PAD/appScreenshots" to listOf(StoreFixtures.EMPTY),
                "POST appScreenshots" to listOf(reservation("NEW1", first)),
                "PATCH appScreenshots/NEW1" to listOf("""{"data":{"id":"NEW1"}}"""),
            ),
        )

        harness.services.replacement.replace("LOC", ScreenshotSlot.IPAD, listOf(first))

        val create = harness.executor.write("POST appScreenshotSets").data()
        assertEquals("APP_IPAD_PRO_3GEN_129", create["attributes"]!!.jsonObject.text("screenshotDisplayType"))
        assertEquals(
            "LOC",
            create["relationships"]!!.jsonObject["appStoreVersionLocalization"]!!.jsonObject["data"]!!.jsonObject.text("id"),
        )
    }

    @Test
    fun `leaves a set alone when it already holds the same delivered files`() {
        val harness = StoreHarness(
            mapOf(
                "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to listOf(
                    """{"data":[{"id":"SET","attributes":{"screenshotDisplayType":"APP_IPHONE_67"}}]}""",
                ),
                "GET appScreenshotSets/SET/appScreenshots" to listOf(
                    list(
                        screenshot("A", "COMPLETE", first.fileName, first.checksum),
                        screenshot("B", "COMPLETE", second.fileName, second.checksum),
                    ),
                ),
            ),
        )

        val outcome = harness.services.replacement.replace("LOC", ScreenshotSlot.IPHONE, listOf(first, second))

        assertEquals(SetOutcome.UNCHANGED, outcome.outcome)
        assertTrue(harness.executor.writes.isEmpty())
        assertTrue(harness.transport.parts.isEmpty())
    }

    @Test
    fun `refuses a reservation whose upload goes anywhere but apple over https, before sending anything`() {
        val harness = StoreHarness(
            mapOf(
                "GET appStoreVersionLocalizations/LOC/appScreenshotSets" to listOf(
                    """{"data":[{"id":"SET","attributes":{"screenshotDisplayType":"APP_IPHONE_67"}}]}""",
                ),
                "GET appScreenshotSets/SET/appScreenshots" to listOf(StoreFixtures.EMPTY),
                "POST appScreenshots" to listOf(reservation("NEW1", first, url = "http://store-uploads.example.apple.com/part")),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> {
            harness.services.replacement.replace("LOC", ScreenshotSlot.IPHONE, listOf(first))
        }

        assertEquals(ErrorCode.UPLOAD_REFUSED, failure.code)
        assertTrue(harness.transport.parts.isEmpty())
        assertFalse(harness.executor.writes.any { it.method == HttpMethod.PATCH })
    }

    @Test
    fun `waits until every screenshot is delivered`() {
        val harness = StoreHarness(
            mapOf(
                "GET appScreenshotSets/SET/appScreenshots" to listOf(
                    list(screenshot("A", "UPLOAD_COMPLETE")),
                    list(screenshot("A", "UPLOAD_COMPLETE")),
                    list(screenshot("A", "COMPLETE")),
                ),
            ),
        )

        harness.services.replacement.awaitDelivery(listOf("SET"))

        assertEquals(2, harness.slept.size)
        assertEquals(3, harness.executor.requests.size)
    }

    @Test
    fun `reports a screenshot App Store Connect could not process with its error codes only`() {
        val failed = """{"id":"A","attributes":{"assetDeliveryState":{"state":"FAILED","errors":[{"code":"IMAGE_INCORRECT_DIMENSIONS",""" +
            """"description":"secret detail"}]}}}"""
        val harness = StoreHarness(mapOf("GET appScreenshotSets/SET/appScreenshots" to listOf(list(failed))))

        val failure = assertFailsWith<ProvisioningException> { harness.services.replacement.awaitDelivery(listOf("SET")) }

        assertEquals(ErrorCode.SCREENSHOTS_NOT_DELIVERED, failure.code)
        assertTrue(failure.message.orEmpty().contains("IMAGE_INCORRECT_DIMENSIONS"))
        assertFalse(failure.message.orEmpty().contains("secret detail"))
    }

    @Test
    fun `stops waiting once the delivery deadline has passed`() {
        val harness = StoreHarness(mapOf("GET appScreenshotSets/SET/appScreenshots" to listOf(list(screenshot("A", "UPLOAD_COMPLETE")))))

        val failure = assertFailsWith<ProvisioningException> { harness.services.replacement.awaitDelivery(listOf("SET")) }

        assertEquals(ErrorCode.SCREENSHOTS_NOT_DELIVERED, failure.code)
        assertTrue(harness.slept.isNotEmpty())
    }

    private fun reservation(
        id: String,
        file: ScreenshotFile,
        url: String = UPLOAD_URL,
    ): String = """{"data":{"id":"$id","attributes":{"fileName":"${file.fileName}","uploadOperations":[{"method":"PUT",""" +
        """"url":"$url","offset":0,"length":${file.bytes.size},"requestHeaders":[{"name":"Content-Type","value":"image/png"}]}]}}}"""
}
