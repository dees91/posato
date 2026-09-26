package app.posato.buildlogic

import org.gradle.api.GradleException
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Whether a release build number may follow the stable builds already published. */
sealed interface ReleaseFloor {
    /** The build number exceeds [floor], the highest build it must follow. */
    data class Above(
        val floor: Long,
    ) : ReleaseFloor

    /** The build number may not be released; [reason] names the numbers involved. */
    data class Refused(
        val reason: String,
    ) : ReleaseFloor
}

/** Reads the highest build number of the published stable feed, so a release cannot reuse or undercut it. */
object PosatoPublishedFeed {
    private const val HTTP_OK = 200
    private const val HTTP_NOT_FOUND = 404
    private const val MAXIMUM_FEED_BYTES = 256 * 1024
    private val connectTimeout = Duration.ofSeconds(10)
    private val totalTimeout = Duration.ofSeconds(30)
    private val buildNumberValue = Regex("""[1-9][0-9]{0,8}""")
    private const val PREVIOUS_PROPERTY = "-PposatoMacOsPreviousBuildNumber"

    /**
     * The highest build in the stable feed published at [feedUrl], or null when no feed is published there (HTTP 404).
     * Follows GitHub's redirects to the release asset over HTTPS only; any other failure stops the release.
     */
    fun publishedBuildNumber(feedUrl: String): Long? {
        verifyContract()
        val feed = fetch(feedUrl) ?: return null
        return highestBuildNumber(feed)
            ?: throw GradleException(
                "The published stable feed at $feedUrl is unreadable: it must be well-formed XML with at least one item, " +
                    "each carrying one positive integer sparkle:version. Fix the published feed before releasing.",
            )
    }

    /** Decides whether [buildNumber] may be released after the [published] stable build and the [explicitPrevious] floor. */
    fun releaseFloor(
        buildNumber: String,
        published: Long?,
        explicitPrevious: String?,
    ): ReleaseFloor {
        verifyContract()
        return decide(buildNumber, published, explicitPrevious)
    }

    /** The highest positive integer sparkle:version among the feed's items; null when any item lacks one or the feed is malformed. */
    internal fun highestBuildNumber(feed: ByteArray): Long? {
        val items = PosatoUpdateFeed.parse(feed)?.getElementsByTagName("item") ?: return null
        if (items.length == 0) return null
        val versions = (0 until items.length).map { index ->
            val versionElements = (items.item(index) as Element).getElementsByTagNameNS(PosatoUpdateFeed.SPARKLE_NAMESPACE, "version")
            if (versionElements.length != 1) return null
            versionElements.item(0).textContent.trim().takeIf(buildNumberValue::matches)?.toLong() ?: return null
        }
        return versions.max()
    }

    private fun decide(
        buildNumber: String,
        published: Long?,
        explicitPrevious: String?,
    ): ReleaseFloor {
        if (!buildNumberValue.matches(buildNumber)) return ReleaseFloor.Refused("The release build number $buildNumber is not a positive integer.")
        if (explicitPrevious != null && !buildNumberValue.matches(explicitPrevious)) {
            return ReleaseFloor.Refused("$PREVIOUS_PROPERTY=$explicitPrevious is not a positive integer.")
        }
        if (published == null && explicitPrevious == null) {
            return ReleaseFloor.Refused(
                "No stable feed is published (HTTP $HTTP_NOT_FOUND: no release, or the latest one has no appcast.xml), " +
                    "so a release feed needs $PREVIOUS_PROPERTY=<previous stable build>.",
            )
        }
        val floor = maxOf(published ?: 0L, explicitPrevious?.toLong() ?: 0L)
        if (buildNumber.toLong() > floor) return ReleaseFloor.Above(floor)
        val publishedText = published?.toString() ?: "none (HTTP $HTTP_NOT_FOUND)"
        return ReleaseFloor.Refused(
            "The release build number $buildNumber must be greater than $floor: the published stable feed carries build " +
                "$publishedText and $PREVIOUS_PROPERTY is ${explicitPrevious ?: "not set"}.",
        )
    }

    private fun fetch(feedUrl: String): ByteArray? {
        val uri = URI(feedUrl)
        if (uri.scheme != "https") throw GradleException("The published stable feed must be read over HTTPS, not $feedUrl.")
        val client = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        try {
            val response = exchange(client, HttpRequest.newBuilder(uri).timeout(totalTimeout).GET().build(), feedUrl)
            if (response.uri().scheme != "https") throw unreachable(feedUrl, "it redirected away from HTTPS", null)
            return when (response.statusCode()) {
                HTTP_OK -> response.body()
                HTTP_NOT_FOUND -> null
                else -> throw unreachable(feedUrl, "HTTP ${response.statusCode()}", null)
            }
        } finally {
            client.shutdownNow()
        }
    }

    private fun exchange(
        client: HttpClient,
        request: HttpRequest,
        feedUrl: String,
    ): HttpResponse<ByteArray> {
        val exchange = client.sendAsync(request) { info ->
            if (info.statusCode() == HTTP_OK) CappedBody(MAXIMUM_FEED_BYTES) else HttpResponse.BodySubscribers.replacing(ByteArray(0))
        }
        return try {
            exchange.get(totalTimeout.toSeconds(), TimeUnit.SECONDS)
        } catch (timeout: TimeoutException) {
            exchange.cancel(true)
            throw unreachable(feedUrl, "no complete response within ${totalTimeout.toSeconds()} seconds", timeout)
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            throw unreachable(feedUrl, cause?.message ?: cause?.javaClass?.simpleName ?: "the request failed", failure)
        } catch (interrupted: InterruptedException) {
            exchange.cancel(true)
            Thread.currentThread().interrupt()
            throw unreachable(feedUrl, "the build was interrupted", interrupted)
        }
    }

    private fun unreachable(
        feedUrl: String,
        reason: String,
        cause: Throwable?,
    ) = GradleException(
        "Could not read the published stable feed at $feedUrl ($reason). The release floor comes from that feed; " +
            "check the network and the latest GitHub release, then rerun.",
        cause,
    )

    /** Collects a response body and fails once it exceeds [limit] bytes. */
    private class CappedBody(
        private val limit: Int,
    ) : HttpResponse.BodySubscriber<ByteArray> {
        private val result = CompletableFuture<ByteArray>()
        private val buffer = ByteArrayOutputStream()
        private var subscription: Flow.Subscription? = null

        override fun getBody(): CompletionStage<ByteArray> = result

        override fun onSubscribe(subscription: Flow.Subscription) {
            this.subscription = subscription
            subscription.request(Long.MAX_VALUE)
        }

        override fun onNext(item: List<ByteBuffer>) {
            if (result.isDone) return
            item.forEach { chunk ->
                val bytes = ByteArray(chunk.remaining())
                chunk.get(bytes)
                buffer.write(bytes)
            }
            if (buffer.size() > limit) {
                result.completeExceptionally(IOException("the feed is larger than $limit bytes"))
                subscription?.cancel()
            }
        }

        override fun onError(throwable: Throwable) {
            result.completeExceptionally(throwable)
        }

        override fun onComplete() {
            result.complete(buffer.toByteArray())
        }
    }

    /** Synthetic feeds and floors: the parser must find the highest build and the decision must refuse every stale number. */
    internal fun verifyContract() {
        val sparkle = PosatoUpdateFeed.SPARKLE_NAMESPACE
        val header = """<?xml version="1.0"?><rss xmlns:sparkle="$sparkle" version="2.0"><channel>"""
        val feed = { items: String -> "$header$items</channel></rss>".toByteArray() }
        val item = { version: String -> "<item><sparkle:version>$version</sparkle:version></item>" }
        val parserHolds = highestBuildNumber(feed(item("26"))) == 26L &&
            highestBuildNumber(feed(item("20") + item("26") + item("3"))) == 26L &&
            highestBuildNumber(feed("<item>\n  <sparkle:version>\n\t27 \n</sparkle:version>\n</item>")) == 27L &&
            highestBuildNumber(
                """<rss xmlns:s="$sparkle" xmlns:other="urn:example"><channel><s:version>99</s:version>""".toByteArray() +
                    """<item><other:version>98</other:version><s:version>21</s:version></item></channel></rss>""".toByteArray(),
            ) == 21L &&
            highestBuildNumber(feed("")) == null &&
            highestBuildNumber(feed("<item><title>1.1.0</title></item>")) == null &&
            highestBuildNumber(feed(item("26") + "<item><version>30</version></item>")) == null &&
            highestBuildNumber(feed("<item><sparkle:version>26</sparkle:version><sparkle:version>27</sparkle:version></item>")) == null &&
            listOf("1.1.0", "0", "-1", "", "1000000000").all { version -> highestBuildNumber(feed(item(version))) == null } &&
            highestBuildNumber("<rss><channel><item>".toByteArray()) == null &&
            highestBuildNumber(
                """<!DOCTYPE rss [<!ENTITY v "26">]><rss xmlns:sparkle="$sparkle"><item><sparkle:version>&v;</sparkle:version></item></rss>"""
                    .toByteArray(),
            ) == null
        val refusal = decide("26", 26L, "20")
        val decisionHolds = decide("27", 26L, null) == ReleaseFloor.Above(26L) &&
            refusal is ReleaseFloor.Refused && "26" in refusal.reason && "20" in refusal.reason &&
            decide("26", null, "25") == ReleaseFloor.Above(25L) &&
            decide("25", null, "25") is ReleaseFloor.Refused &&
            decide("31", 26L, "30") == ReleaseFloor.Above(30L) &&
            decide("30", 26L, "30") is ReleaseFloor.Refused &&
            decide("27", 26L, "20") == ReleaseFloor.Above(26L) &&
            decide("26", null, null) is ReleaseFloor.Refused &&
            listOf("0", "x", "1.0").all { previous -> decide("27", 26L, previous) is ReleaseFloor.Refused } &&
            decide("1.1.0", 26L, null) is ReleaseFloor.Refused
        if (!parserHolds || !decisionHolds) {
            throw GradleException("The Posato published-feed floor failed its regression contract.")
        }
    }
}
