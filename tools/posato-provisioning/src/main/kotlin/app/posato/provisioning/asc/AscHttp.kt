package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Transcript
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration

private const val BASE_URI = "https://api.appstoreconnect.apple.com/v1/"
private val TOTAL_DEADLINE: Duration = Duration.ofSeconds(60)
private const val MAX_BODY_BYTES = 4 * 1024 * 1024
private const val MAX_GET_ATTEMPTS = 3
private const val SUCCESS_FLOOR = 200
private const val SUCCESS_CEILING = 299
private val FIRST_BACKOFF: Duration = Duration.ofSeconds(1)

/** Paths are built from constants; this rejects anything an identifier could smuggle into a URL. */
private val PATH_PATTERN = Regex("[A-Za-z0-9/_-]+")

fun interface Sleeper {
    fun sleep(duration: Duration)
}

/**
 * Every App Store Connect request, bounded in each direction.
 *
 * Retries are limited to `GET`. A `POST`, `PATCH`, or `DELETE` that times out may already have been applied, so
 * repeating it would create a duplicate certificate against Apple's per-team cap, a conflicting device, a second
 * profile claiming a name that must be unique, or a second screenshot reservation. Failing closed after one attempt is
 * safe because rerunning the command reuses whatever the first attempt actually created or changed.
 */
class AscHttp(
    private val tokenSource: TokenSource,
    private val transcript: Transcript,
    private val clock: Clock,
    private val sleeper: Sleeper,
    private val exchange: HttpExchange = JdkHttpExchange(),
) : AscRequestExecutor {
    override fun execute(request: AscRequest): AscResponse {
        val deadline = clock.instant().plus(TOTAL_DEADLINE)
        val attempts = if (request.method == HttpMethod.GET) MAX_GET_ATTEMPTS else 1
        var failure = AscError.unreachable(null)
        for (index in 0 until attempts) {
            if (index > 0) sleeper.sleep(FIRST_BACKOFF.multipliedBy(1L shl (index - 1)))
            val remaining = Duration.between(clock.instant(), deadline)
            if (remaining.isNegative || remaining.isZero) break
            when (val attempt = attemptOnce(request, remaining)) {
                is Attempt.Succeeded -> {
                    return attempt.response
                }

                is Attempt.Failed -> {
                    if (!attempt.retryable) throw attempt.failure
                    failure = attempt.failure
                }
            }
        }
        throw failure
    }

    private fun attemptOnce(
        request: AscRequest,
        remaining: Duration
    ): Attempt {
        var transport: Exception? = null
        val response = try {
            send(request, remaining)
        } catch (exception: IOException) {
            transport = exception
            null
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            transport = exception
            null
        }
        return when {
            response == null -> Attempt.Failed(AscError.unreachable(transport), transport is IOException)
            response.status in SUCCESS_FLOOR..SUCCESS_CEILING -> Attempt.Succeeded(response)
            else -> Attempt.Failed(AscError.of(response), AscError.isRetryable(response.status))
        }
    }

    private fun send(
        request: AscRequest,
        remaining: Duration
    ): AscResponse {
        transcript.record("${request.method} ${request.path}")
        val uri = uriFor(request)
        val raw = exchange.send(uri, request.method, request.body, tokenSource.token(), remaining, MAX_BODY_BYTES)
        val body = raw.body
        if (body.size > MAX_BODY_BYTES) {
            throw ProvisioningException(
                ErrorCode.ASC_RESPONSE_TOO_LARGE,
                "App Store Connect returned more than $MAX_BODY_BYTES bytes for ${request.path}.",
                "Rerun the command; if it repeats, the account holds more resources than this tool expects.",
            )
        }
        return AscResponse(raw.status, String(body, StandardCharsets.UTF_8))
    }

    private fun uriFor(request: AscRequest): URI {
        if (!request.path.matches(PATH_PATTERN)) {
            throw ProvisioningException(
                ErrorCode.COMMAND_FAILED,
                "An App Store Connect path this tool built is not a plain resource path.",
                "This is a defect in the tool rather than a configuration problem.",
            )
        }
        val query = request.query
            .takeIf { it.isNotEmpty() }
            ?.joinToString("&", prefix = "?") { (name, value) -> "${encode(name)}=${encode(value)}" }
            .orEmpty()
        return URI.create("$BASE_URI${request.path}$query")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private sealed interface Attempt {
        data class Succeeded(
            val response: AscResponse
        ) : Attempt

        data class Failed(
            val failure: ProvisioningException,
            val retryable: Boolean
        ) : Attempt
    }
}
