package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.AppIdentifier
import app.posato.provisioning.model.ApplePlatform
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AscClientTest {
    @Test
    fun `matches an App ID exactly so a prefix does not select the extension`() {
        val executor = RecordingExecutor(
            """
            {"data":[
              {"id":"EXT","attributes":{"identifier":"app.posato.ios.activitymonitor"}},
              {"id":"APP","attributes":{"identifier":"app.posato.ios"}}
            ]}
            """.trimIndent(),
        )

        val client = AscClient(executor)

        assertEquals("APP", client.bundleId(AppIdentifier.IOS_APPLICATION)?.id)
        assertEquals("EXT", client.bundleId(AppIdentifier.IOS_ACTIVITY_MONITOR, client.bundleIds())?.id)
        assertNull(client.bundleId(AppIdentifier.MACOS_SYNC, client.bundleIds()))
    }

    @Test
    fun `asks for one bounded page of each resource`() {
        val executor = RecordingExecutor("""{"data":[]}""")
        val client = AscClient(executor)

        client.bundleIds()
        client.devices()
        client.certificates()
        client.profiles()

        assertEquals(listOf("bundleIds", "devices", "certificates", "profiles"), executor.requests.map { it.path })
        assertTrue(executor.requests.all { request -> request.query.contains("limit" to "200") })
        assertTrue(executor.requests.all { request -> request.method == HttpMethod.GET })
        assertTrue(executor.requests[2].query.contains("filter[certificateType]" to "DEVELOPMENT"))
        assertTrue(executor.requests[3].query.contains("include" to "bundleId,certificates,devices"))
    }

    @Test
    fun `stops rather than following a page App Store Connect supplies`() {
        val executor = RecordingExecutor("""{"data":[],"links":{"next":"https://api.appstoreconnect.apple.com/v1/devices?cursor=x"}}""")

        val failure = assertFailsWith<ProvisioningException> { AscClient(executor).devices() }

        assertEquals(ErrorCode.ASC_TOO_MANY_RESULTS, failure.code)
        assertEquals(1, executor.requests.size)
    }

    @Test
    fun `sends the device body App Store Connect expects`() {
        val executor = RecordingExecutor("""{"data":{"id":"DEV","attributes":{"udid":"u","status":"ENABLED"}}}""")

        val device = AscClient(executor).createDevice("Posato Development Mac", ApplePlatform.MACOS, "UDID-VALUE")

        assertEquals("DEV", device.id)
        val request = executor.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("devices", request.path)
        val attributes = body(request)["data"]!!.jsonObject["attributes"]!!.jsonObject
        assertEquals("devices", body(request)["data"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("Posato Development Mac", attributes["name"]!!.jsonPrimitive.content)
        assertEquals("MAC_OS", attributes["platform"]!!.jsonPrimitive.content)
        assertEquals("UDID-VALUE", attributes["udid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sends the certificate signing request as a development certificate`() {
        val executor = RecordingExecutor("""{"data":{"id":"CERT","attributes":{"certificateType":"DEVELOPMENT"}}}""")

        AscClient(executor).createCertificate("-----BEGIN CERTIFICATE REQUEST-----\nAAAA\n-----END CERTIFICATE REQUEST-----")

        val attributes = body(executor.requests.single())["data"]!!.jsonObject["attributes"]!!.jsonObject
        assertEquals("DEVELOPMENT", attributes["certificateType"]!!.jsonPrimitive.content)
        assertTrue(attributes["csrContent"]!!.jsonPrimitive.content.startsWith("-----BEGIN CERTIFICATE REQUEST-----"))
    }

    @Test
    fun `sends the profile relationships that decide what the profile grants`() {
        val executor = RecordingExecutor("""{"data":{"id":"PROF","attributes":{"name":"Posato_macOS_Sync_Development"}}}""")

        AscClient(executor).createProfile(
            name = AppIdentifier.MACOS_SYNC.profileName,
            profileType = AppIdentifier.MACOS_SYNC.profileType,
            bundleIdId = "BID",
            certificateIds = listOf("CERT"),
            deviceIds = listOf("MAC", "PHONE"),
        )

        val data = body(executor.requests.single())["data"]!!.jsonObject
        assertEquals("Posato_macOS_Sync_Development", data["attributes"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("MAC_APP_DEVELOPMENT", data["attributes"]!!.jsonObject["profileType"]!!.jsonPrimitive.content)
        val relationships = data["relationships"]!!.jsonObject
        assertEquals("BID", relationships["bundleId"]!!.jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(listOf("CERT"), ids(relationships, "certificates"))
        assertEquals(listOf("MAC", "PHONE"), ids(relationships, "devices"))
    }

    @Test
    fun `deletes a profile by its own identifier`() {
        val executor = RecordingExecutor("")

        AscClient(executor).deleteProfile("PROF")

        assertEquals(HttpMethod.DELETE, executor.requests.single().method)
        assertEquals("profiles/PROF", executor.requests.single().path)
    }

    @Test
    fun `reads the relationships and attributes a stored profile carries`() {
        val executor = RecordingExecutor(
            """
            {"data":[{"id":"PROF","attributes":{"name":"Posato_macOS_Sync_Development","profileState":"ACTIVE",
            "expirationDate":"2027-01-01T00:00:00.000+0000","uuid":"UUID","profileContent":"QUJD"},
            "relationships":{"bundleId":{"data":{"id":"BID","type":"bundleIds"}},
            "certificates":{"data":[{"id":"CERT","type":"certificates"}]},
            "devices":{"data":[{"id":"MAC","type":"devices"}]}}}]}
            """.trimIndent(),
        )

        val profile = AscClient(executor).profiles().single()

        assertTrue(profile.active)
        assertEquals("UUID", profile.attributes.uuid)
        assertEquals(listOf("CERT"), profile.relationships.certificates.data.map { it.id })
        assertEquals(listOf("MAC"), profile.relationships.devices.data.map { it.id })
        assertEquals("BID", profile.relationships.bundleId.data?.id)
    }

    private fun body(request: AscRequest): JsonObject = ProvisioningJson.lenient.decodeFromString(JsonObject.serializer(), request.body.orEmpty())

    private fun ids(
        relationships: JsonObject,
        name: String
    ): List<String> = relationships[name]!!
        .jsonObject["data"]!!
        .jsonArray
        .map { entry -> entry.jsonObject["id"]!!.jsonPrimitive.content }
}
