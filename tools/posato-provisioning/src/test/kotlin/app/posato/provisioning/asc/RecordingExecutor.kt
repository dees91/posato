package app.posato.provisioning.asc

/** Records what the client asked for and replays a scripted body, so request shapes are asserted without a network. */
class RecordingExecutor(
    private val bodies: List<String>
) : AscRequestExecutor {
    val requests = mutableListOf<AscRequest>()

    constructor(body: String) : this(listOf(body))

    override fun execute(request: AscRequest): AscResponse {
        val body = bodies[requests.size.coerceAtMost(bodies.size - 1)]
        requests.add(request)
        return AscResponse(200, body)
    }
}
