package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Transcript
import app.posato.provisioning.model.UploadOperation
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val UPLOAD_METHOD = "PUT"
private const val UPLOAD_SCHEME = "https"
private const val APPLE_HOST_SUFFIX = ".apple.com"
private const val HTTPS_PORT = 443
private const val DEFAULT_PORT = -1
private const val SUCCESS_FLOOR = 200
private const val SUCCESS_CEILING = 299
private val PART_TIMEOUT: Duration = Duration.ofSeconds(120)
private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val HEADER_NAME = Regex("[A-Za-z0-9-]+")
private val HEADER_VALUE = Regex("[\\x20-\\x7E]*")

/** The HTTP client derives these from the validated URL and the exact slice, so a matching value is simply not repeated. */
private const val CONTENT_LENGTH = "content-length"
private const val HOST = "host"

/** Headers that would change how the connection or the exchange behaves rather than describe the part. */
private val REFUSED_HEADERS = setOf("connection", "expect", "upgrade", "transfer-encoding")

/** One validated upload part: where it goes, which headers it carries, and which bytes of the file it sends. */
data class UploadPart(
    val uri: URI,
    val headers: List<Pair<String, String>>,
    val offset: Int,
    val length: Int,
)

/**
 * The one place this tool accepts a URL App Store Connect supplied.
 *
 * A screenshot's bytes can only go where the reservation says, so these URLs cannot be avoided the way paging is.
 * They are held to the narrowest shape that works: method `PUT`, scheme `https`, a host under `apple.com` on the
 * default port, no user information, headers that are plain tokens and printable values, and a slice that lies
 * inside the file. Anything else refuses the whole upload before a single byte is sent. No failure repeats the URL,
 * because an upload URL may carry a signature in its query.
 */
object UploadPolicy {
    fun validate(
        operation: UploadOperation,
        fileSize: Int,
    ): UploadPart {
        ensure(operation.method == UPLOAD_METHOD) { "its method is not PUT" }
        val uri = parse(operation.url)
        ensure(uri.scheme == UPLOAD_SCHEME) { "its URL is not https" }
        val host = uri.host?.lowercase().orEmpty()
        ensure(host.endsWith(APPLE_HOST_SUFFIX) && host.length > APPLE_HOST_SUFFIX.length) { "its host is not under apple.com" }
        ensure(uri.rawUserInfo == null) { "its URL carries user information" }
        ensure(uri.port == DEFAULT_PORT || uri.port == HTTPS_PORT) { "its URL names a port other than 443" }
        val offset = operation.offset ?: -1
        val length = operation.length ?: 0
        ensure(offset >= 0 && length > 0 && offset + length <= fileSize) { "its byte range does not lie inside the file" }
        return UploadPart(uri, headers(operation, host, length), offset.toInt(), length.toInt())
    }

    private fun headers(
        operation: UploadOperation,
        host: String,
        length: Long,
    ): List<Pair<String, String>> = operation.requestHeaders.mapNotNull { header ->
        val name = header.name.orEmpty()
        val value = header.value.orEmpty()
        ensure(name.matches(HEADER_NAME) && value.matches(HEADER_VALUE)) { "one of its headers is not a plain name and value" }
        when (name.lowercase()) {
            in REFUSED_HEADERS -> refuse("it sets the $name header")
            CONTENT_LENGTH -> null.also { ensure(value == length.toString()) { "its Content-Length does not match its byte range" } }
            HOST -> null.also { ensure(value.lowercase() == host) { "its Host header does not match its URL" } }
            else -> name to value
        }
    }

    private fun parse(url: String?): URI = try {
        URI(url.orEmpty())
    } catch (exception: URISyntaxException) {
        throw refusal("its URL cannot be parsed", exception)
    }

    private fun ensure(
        condition: Boolean,
        reason: () -> String
    ) {
        if (!condition) refuse(reason())
    }

    private fun refuse(reason: String): Nothing = throw refusal(reason, null)

    private fun refusal(
        reason: String,
        cause: Throwable?
    ): ProvisioningException = ProvisioningException(
        ErrorCode.UPLOAD_REFUSED,
        "App Store Connect supplied a screenshot upload operation this tool refuses: $reason.",
        "Nothing was uploaded. Rerun `store prepare`; if it repeats, the upload contract has changed and the tool needs review.",
        cause,
    )
}

/** Sends one validated part. The seam lets a test assert exactly what would leave the machine. */
fun interface UploadTransport {
    fun put(
        part: UploadPart,
        body: ByteArray,
        timeout: Duration,
    ): Int
}

/**
 * The transport for upload parts: no redirects, no App Store Connect token, and a deadline that covers the response.
 *
 * The bearer token is deliberately absent. The upload host authorizes the part through its own URL, and the team key's
 * token has no business reaching any host other than the App Store Connect API.
 */
class JdkUploadTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : UploadTransport {
    override fun put(
        part: UploadPart,
        body: ByteArray,
        timeout: Duration,
    ): Int {
        val builder = HttpRequest.newBuilder(part.uri)
            .timeout(timeout)
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
        part.headers.forEach { (name, value) -> builder.header(name, value) }
        val future = client.sendAsync(builder.build(), BodyHandlers.discarding())
        val failure: IOException = try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS).statusCode()
        } catch (exception: TimeoutException) {
            IOException("The upload did not finish within the deadline.", exception)
        } catch (exception: ExecutionException) {
            exception.cause as? IOException ?: IOException("The upload failed.", exception.cause)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            IOException("The upload was interrupted.", exception)
        }
        future.cancel(true)
        throw failure
    }
}

/**
 * Sends a screenshot's bytes to the upload operations its reservation returned.
 *
 * Every operation is validated before any part is sent, so a single refused operation uploads nothing. A part is
 * attempted once: a failed upload leaves an uncommitted reservation, which the next `store prepare` deletes together
 * with the rest of the set before uploading again.
 */
class ScreenshotUploader(
    private val transport: UploadTransport,
    private val transcript: Transcript,
) {
    fun upload(
        operations: List<UploadOperation>,
        bytes: ByteArray,
    ) {
        if (operations.isEmpty()) {
            throw ProvisioningException(
                ErrorCode.UPLOAD_REFUSED,
                "App Store Connect reserved a screenshot without any upload operation.",
                "Rerun `store prepare`; it replaces the incomplete reservation.",
            )
        }
        val parts = operations.map { operation -> UploadPolicy.validate(operation, bytes.size) }
        parts.forEachIndexed { index, part ->
            transcript.record("$UPLOAD_METHOD screenshot upload part ${index + 1} of ${parts.size} (${part.length} bytes)")
            send(part, bytes)
        }
    }

    private fun send(
        part: UploadPart,
        bytes: ByteArray,
    ) {
        val status = try {
            transport.put(part, bytes.copyOfRange(part.offset, part.offset + part.length), PART_TIMEOUT)
        } catch (exception: IOException) {
            throw failed("could not be sent within ${PART_TIMEOUT.toSeconds()} s", exception)
        }
        if (status !in SUCCESS_FLOOR..SUCCESS_CEILING) throw failed("was answered with HTTP $status", null)
    }

    private fun failed(
        reason: String,
        cause: Throwable?
    ): ProvisioningException = ProvisioningException(
        ErrorCode.UPLOAD_FAILED,
        "A screenshot upload part $reason.",
        "Rerun `store prepare`; it replaces the incomplete screenshot set.",
        cause,
    )
}
