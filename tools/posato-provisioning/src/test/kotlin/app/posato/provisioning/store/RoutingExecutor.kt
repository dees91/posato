package app.posato.provisioning.store

import app.posato.provisioning.asc.AscRequest
import app.posato.provisioning.asc.AscRequestExecutor
import app.posato.provisioning.asc.AscResponse
import app.posato.provisioning.asc.HttpMethod
import app.posato.provisioning.asc.MutableClock
import app.posato.provisioning.asc.ReviewClient
import app.posato.provisioning.asc.ScreenshotClient
import app.posato.provisioning.asc.ScreenshotUploader
import app.posato.provisioning.asc.Sleeper
import app.posato.provisioning.asc.StoreClient
import app.posato.provisioning.asc.UploadPart
import app.posato.provisioning.asc.UploadTransport
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.core.Transcript
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant

/**
 * The recording executor for multi-step store flows: it answers by `METHOD path` rather than by call order, so a test
 * scripts an account's state and then asserts exactly which requests a command made. A route answers its bodies in
 * order and repeats the last one; an unscripted request fails the test.
 */
class RoutingExecutor(
    private val routes: Map<String, List<String>>
) : AscRequestExecutor {
    val requests = mutableListOf<AscRequest>()
    private val served = mutableMapOf<String, Int>()

    val writes: List<AscRequest> get() = requests.filter { request -> request.method != HttpMethod.GET }

    override fun execute(request: AscRequest): AscResponse {
        val key = "${request.method} ${request.path}"
        val bodies = routes[key] ?: error("Unscripted request $key")
        val index = served.getOrDefault(key, 0)
        served[key] = index + 1
        requests.add(request)
        return AscResponse(200, bodies[index.coerceAtMost(bodies.size - 1)])
    }

    fun write(key: String): AscRequest = writes.single { request -> "${request.method} ${request.path}" == key }
}

/** Records every part that would leave the machine and answers with a fixed status. */
class RecordingTransport(
    private val status: Int = 200
) : UploadTransport {
    val parts = mutableListOf<Pair<UploadPart, ByteArray>>()

    override fun put(
        part: UploadPart,
        body: ByteArray,
        timeout: Duration,
    ): Int {
        parts.add(part to body)
        return status
    }
}

class StoreHarness(
    routes: Map<String, List<String>>,
    val transport: RecordingTransport = RecordingTransport(),
) {
    val executor = RoutingExecutor(routes)
    val clock = MutableClock(Instant.parse("2026-09-25T12:00:00Z"))
    val slept = mutableListOf<Duration>()
    private val screenshots = ScreenshotClient(executor)
    val services = StoreServices(
        store = StoreClient(executor),
        screenshots = screenshots,
        review = ReviewClient(executor),
        replacement = ScreenshotReplacement(
            screenshots,
            ScreenshotUploader(transport, Transcript { }),
            clock,
            Sleeper { duration ->
                slept.add(duration)
                clock.now = clock.now.plus(duration)
            },
        ),
    )
}

object StoreFixtures {
    const val APPS = """{"data":[{"id":"EXT","attributes":{"bundleId":"app.posato.ios.activitymonitor"}},""" +
        """{"id":"APP","attributes":{"bundleId":"app.posato.ios"}}]}"""

    const val NO_VERSIONS = """{"data":[]}"""

    const val EMPTY = """{"data":[]}"""

    const val NO_BUILD = """{"data":null}"""

    const val ATTACHED_BUILD = """{"data":{"id":"B5","attributes":{"version":"5","processingState":"VALID"}}}"""

    fun version(
        releaseType: String = "AFTER_APPROVAL",
        state: String = "PREPARE_FOR_SUBMISSION"
    ): String = """{"data":[{"id":"VER","attributes":{"platform":"IOS","versionString":"1.2.0",""" +
        """"appVersionState":"$state","releaseType":"$releaseType"}}]}"""

    const val CREATED_VERSION = """{"data":{"id":"VER","attributes":{"versionString":"1.2.0","releaseType":"MANUAL"}}}"""

    fun builds(
        state: String = "VALID",
        marketing: String = "1.2.0"
    ): String = """{"data":[{"id":"B5","attributes":{"version":"5","processingState":"$state","expired":false},""" +
        """"relationships":{"preReleaseVersion":{"data":{"type":"preReleaseVersions","id":"PRV"}}}}],""" +
        """"included":[{"type":"preReleaseVersions","id":"PRV","attributes":{"version":"$marketing","platform":"IOS"}}]}"""

    fun localizations(whatsNew: String): String = """{"data":[{"id":"LOC","attributes":{"locale":"en-US","whatsNew":"$whatsNew"}}]}"""

    fun screenshot(
        id: String,
        state: String,
        fileName: String = "01.png",
        checksum: String = "0",
    ): String = """{"id":"$id","attributes":{"fileName":"$fileName","sourceFileChecksum":"$checksum",""" +
        """"assetDeliveryState":{"state":"$state","errors":[]}}}"""

    fun list(vararg items: String): String = """{"data":[${items.joinToString(",")}]}"""
}

fun AscRequest.json(): JsonObject = ProvisioningJson.lenient.decodeFromString(JsonObject.serializer(), body.orEmpty())

fun AscRequest.data(): JsonObject = json()["data"]!!.jsonObject

fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.content
