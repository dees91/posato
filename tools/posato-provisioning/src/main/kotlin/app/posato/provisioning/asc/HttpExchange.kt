package app.posato.provisioning.asc

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)

data class RawResponse(
    val status: Int,
    val body: InputStream,
)

/**
 * One request on the wire, with the body still unread.
 *
 * The seam sits here rather than around the whole client so the response cap, the retry rule, and the URI shape stay
 * in code a test can drive, leaving only the transport itself unexercised until the live run.
 */
fun interface HttpExchange {
    fun send(
        uri: URI,
        method: HttpMethod,
        body: String?,
        token: String,
        timeout: Duration
    ): RawResponse
}

class JdkHttpExchange(
    private val client: HttpClient = defaultClient()
) : HttpExchange {
    override fun send(
        uri: URI,
        method: HttpMethod,
        body: String?,
        token: String,
        timeout: Duration
    ): RawResponse {
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
        }
        val response = client.send(builder.build(), BodyHandlers.ofInputStream())
        return RawResponse(response.statusCode(), response.body())
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }
}
