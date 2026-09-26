package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.AscOptional
import app.posato.provisioning.model.AscPage
import app.posato.provisioning.model.AscSingle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

private const val PAGE_LIMIT = "200"
private val LIMIT = "limit" to PAGE_LIMIT

/** The remedy when a store listing (versions, localizations, screenshots, submissions) exceeds one page. */
internal const val STORE_LISTING_HINT =
    "Nothing was changed. This listing needs paging support in posato-provisioning; extend the tool rather than " +
        "scripting around it."

/** The first page of a listing, and whether App Store Connect offered another one that this tool did not follow. */
data class FirstPage<T>(
    val data: List<T>,
    val included: List<JsonObject>,
    val hasMore: Boolean,
)

/**
 * Turns a decoding failure into a message that names the resource and nothing else.
 *
 * kotlinx reports a decoding error by quoting the input around the offset. For a `devices` or `certificates`
 * document that slice is other people's device identifiers and certificate bytes, none of which redaction knows
 * about, and it would travel into an envelope that gets pasted into a record.
 */
private fun <T> decodeDocument(
    path: String,
    read: () -> T
): T = try {
    read()
} catch (exception: SerializationException) {
    throw ProvisioningException(
        ErrorCode.ASC_REJECTED,
        "App Store Connect returned a $path document this tool could not read.",
        "Rerun with --verbose; if it repeats, the App Store Connect response shape has changed.",
        exception,
    )
}

/**
 * The JSON:API document handling every named App Store Connect operation shares.
 *
 * There is no paging. A second page means something unexpected, so [list] stops instead of following a URL the
 * service supplied, which removes the need to validate one. [firstPage] is the one deliberate exception for a
 * listing sorted newest first, where the first page is the whole answer and the caller reports that more exist.
 */
class AscDocuments(
    private val executor: AscRequestExecutor
) {
    /** [tooManyHint] is the caller's remedy for a second page, because what to do about one depends on the resource. */
    fun <T> list(
        path: String,
        query: List<Pair<String, String>>,
        serializer: KSerializer<T>,
        tooManyHint: String,
    ): List<T> {
        val page = firstPage(path, query, serializer)
        if (page.hasMore) {
            throw ProvisioningException(
                ErrorCode.ASC_TOO_MANY_RESULTS,
                "The account holds more $path than this tool reads in one page.",
                tooManyHint,
            )
        }
        return page.data
    }

    fun <T> firstPage(
        path: String,
        query: List<Pair<String, String>>,
        serializer: KSerializer<T>,
    ): FirstPage<T> {
        val response = executor.execute(AscRequest(HttpMethod.GET, path, query + LIMIT))
        val decoded = decodeDocument(path) { ProvisioningJson.lenient.decodeFromString(AscPage.serializer(serializer), response.body) }
        return FirstPage(decoded.data, decoded.included, decoded.links.next != null)
    }

    /** A to-one relationship, or `null` when App Store Connect reports nothing attached. */
    fun <T> optional(
        path: String,
        serializer: KSerializer<T>,
    ): T? {
        val response = executor.execute(AscRequest(HttpMethod.GET, path))
        return decodeDocument(path) { ProvisioningJson.lenient.decodeFromString(AscOptional.serializer(serializer), response.body) }.data
    }

    /** A `POST` or `PATCH` whose response document carries the resource it created or changed. */
    fun <T> write(
        method: HttpMethod,
        path: String,
        body: String,
        serializer: KSerializer<T>,
    ): T {
        val response = executor.execute(AscRequest(method, path, emptyList(), body))
        return decodeDocument(path) { ProvisioningJson.lenient.decodeFromString(AscSingle.serializer(serializer), response.body) }.data
    }

    /** A `DELETE` or a relationship `PATCH`, which App Store Connect answers with no content. */
    fun send(
        method: HttpMethod,
        path: String,
        body: String? = null,
    ) {
        executor.execute(AscRequest(method, path, emptyList(), body))
    }

    /** Decodes one included resource the caller already selected by type. */
    fun <T> included(
        path: String,
        element: JsonObject,
        serializer: KSerializer<T>,
    ): T = decodeDocument(path) { ProvisioningJson.lenient.decodeFromJsonElement(serializer, element) }
}
