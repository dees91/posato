package app.posato.provisioning.asc

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

data class RawResponse(
    val status: Int,
    val body: ByteArray,
)

/**
 * One request on the wire, headers and body both read inside the deadline.
 *
 * The seam sits here rather than around the whole client so the retry rule, the size decision, and the URI shape
 * stay in code a test can drive, leaving only the transport itself unexercised until a live run.
 *
 * `maxBytes` bounds what is read, not what is accepted: the exchange reads one byte past the cap so the caller can
 * tell a response that is exactly at the limit from one that exceeds it.
 */
fun interface HttpExchange {
    fun send(
        uri: URI,
        method: HttpMethod,
        body: String?,
        token: String,
        timeout: Duration,
        maxBytes: Int,
    ): RawResponse
}

class JdkHttpExchange(
    private val client: HttpClient = defaultClient()
) : HttpExchange {
    /**
     * The deadline covers reading the body, not just receiving the headers.
     *
     * `send` with a streaming body handler returns as soon as the headers arrive, and the request timeout stops
     * applying at that point, so a peer that stalls mid-body would hang the command indefinitely past the bound the
     * tool promises. Reading inside the future and bounding the whole future closes that.
     */
    override fun send(
        uri: URI,
        method: HttpMethod,
        body: String?,
        token: String,
        timeout: Duration,
        maxBytes: Int,
    ): RawResponse {
        val future: CompletableFuture<RawResponse> = client
            .sendAsync(build(uri, method, body, token, timeout), BodyHandlers.ofInputStream())
            .thenApply { response ->
                RawResponse(response.statusCode(), response.body().use { stream -> stream.readNBytes(maxBytes + 1) })
            }
        // One throw site, so every transport failure reaches the caller as an IOException it already knows how to
        // categorise, and the future is cancelled on every path rather than left reading in the background.
        val failure: IOException = try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (exception: TimeoutException) {
            HttpTimeoutException("App Store Connect did not answer within the deadline.").apply { initCause(exception) }
        } catch (exception: ExecutionException) {
            exception.cause as? IOException ?: IOException("The App Store Connect request failed.", exception.cause)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            IOException("The App Store Connect request was interrupted.", exception)
        }
        future.cancel(true)
        throw failure
    }

    private fun build(
        uri: URI,
        method: HttpMethod,
        body: String?,
        token: String,
        timeout: Duration
    ): HttpRequest {
        val builder = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
        when (method) {
            HttpMethod.GET -> {
                builder.GET()
            }

            HttpMethod.DELETE -> {
                builder.DELETE()
            }

            HttpMethod.POST -> {
                builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.orEmpty()))
            }

            HttpMethod.PATCH -> {
                builder
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.orEmpty()))
            }
        }
        return builder.build()
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }
}
