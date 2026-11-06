package app.posato.provisioning.play

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)

enum class WireMethod { GET, POST, PUT, DELETE }

/** One request as it leaves the machine: every header the tool chose, and the exact body bytes. */
class WireRequest(
    val method: WireMethod,
    val uri: URI,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeout: Duration,
)

/** The status, the body read up to one byte past the cap, and the `Location` header a resumable upload answers with. */
class WireResponse(
    val status: Int,
    val body: ByteArray,
    val location: String? = null,
)

/**
 * The seam between the Play operations and the network, so the edit workflow, the token exchange, and the upload
 * policy are driven in tests without a Play account.
 */
fun interface PlayTransport {
    fun send(
        request: WireRequest,
        maxBytes: Int,
    ): WireResponse
}

/**
 * The JDK transport: redirects refused, and one deadline that covers reading the body as well as the headers, for the
 * same reason as the App Store Connect exchange.
 */
class JdkPlayTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : PlayTransport {
    override fun send(
        request: WireRequest,
        maxBytes: Int,
    ): WireResponse {
        val future: CompletableFuture<WireResponse> = client
            .sendAsync(build(request), BodyHandlers.ofInputStream())
            .thenApply { response ->
                val body = response.body().use { stream -> stream.readNBytes(maxBytes + 1) }
                WireResponse(response.statusCode(), body, response.headers().firstValue("location").orElse(null))
            }
        val failure: IOException = try {
            return future.get(request.timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (exception: TimeoutException) {
            HttpTimeoutException("Google did not answer within the deadline.").apply { initCause(exception) }
        } catch (exception: ExecutionException) {
            exception.cause as? IOException ?: IOException("The Google request failed.", exception.cause)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            IOException("The Google request was interrupted.", exception)
        }
        future.cancel(true)
        throw failure
    }

    private fun build(request: WireRequest): HttpRequest {
        val builder = HttpRequest.newBuilder(request.uri).timeout(request.timeout)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val body = request.body?.let { bytes -> HttpRequest.BodyPublishers.ofByteArray(bytes) } ?: HttpRequest.BodyPublishers.noBody()
        return builder.method(request.method.name, body).build()
    }
}
