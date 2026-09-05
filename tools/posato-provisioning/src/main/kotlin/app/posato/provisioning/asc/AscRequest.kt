package app.posato.provisioning.asc

enum class HttpMethod { GET, POST, DELETE }

data class AscRequest(
    val method: HttpMethod,
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val body: String? = null,
)

data class AscResponse(
    val status: Int,
    val body: String,
)

/**
 * The seam between the operations this tool performs and the network.
 *
 * Every test drives the operations through a recording implementation, so request shapes, idempotence, and error
 * handling are all checked without an Apple account or a live connection.
 */
fun interface AscRequestExecutor {
    fun execute(request: AscRequest): AscResponse
}
