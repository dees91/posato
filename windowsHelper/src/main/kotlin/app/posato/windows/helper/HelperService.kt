package app.posato.windows.helper

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The service of ADR 0010 on Windows. It answers one request per connection from the installing user (or the
 * system), keeps the block filters and the state file in step, resolves the paused names again every minute and only
 * adds addresses, ends the chosen programs while a pause runs, and clears a pause by itself at its end. It never
 * reads traffic and never logs hosts.
 */
internal class HelperService(
    private val folder: HelperFolder,
    config: HelperConfig,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val filters = WfpFilters(config.sid)
    private val ender = ProcessEnder(config.sid)
    private val timer: ScheduledExecutorService = Executors.newScheduledThreadPool(2, daemon("posato-helper-timer"))
    private val resolvers = Executors.newFixedThreadPool(RESOLVER_THREADS, daemon("posato-helper-resolver"))
    private val workers = Executors.newFixedThreadPool(CONNECTION_WORKERS, daemon("posato-helper-connection"))
    private val server = PipeServer(config.sid, workers, ::answer)
    private var state = folder.read(folder.state)?.let(HelperState::decode) ?: HelperState.EMPTY

    /** Serves until [stop]; false when the pipe name is already taken, so the service should fail and restart. */
    fun serve(): Boolean {
        timer.scheduleWithFixedDelay(::tick, 0, TICK_MILLIS, TimeUnit.MILLISECONDS)
        timer.scheduleWithFixedDelay(::refresh, 0, REFRESH_MILLIS, TimeUnit.MILLISECONDS)
        return server.serve()
    }

    /** Stops serving; closing the filtering session removes every filter, and a restart applies the state again. */
    fun stop() {
        server.stop()
        timer.shutdownNow()
        resolvers.shutdownNow()
        workers.shutdownNow()
        synchronized(lock) { filters.close() }
    }

    fun handle(request: HelperRequest): String {
        return when (request) {
            is HelperRequest.Apply -> apply(request)

            HelperRequest.Clear -> synchronized(lock) {
                if (filters.replace(emptySet()) && save(state.copy(applied = null))) "ok" else "error\tclear"
            }

            HelperRequest.Status -> synchronized(lock) {
                "ok\t${state.applied?.sessionId ?: "-"}\t${state.expiredSessionId ?: "-"}"
            }

            is HelperRequest.Acknowledge -> synchronized(lock) {
                if (state.expiredSessionId == request.sessionId) save(state.copy(expiredSessionId = null))
                "ok"
            }
        }
    }

    private fun answer(line: String): String {
        return HelperRequest.parse(line)?.let(::handle) ?: "error\trequest"
    }

    /** Names are resolved before the lock is taken, so the old filters hold until the new ones replace them. */
    private fun apply(request: HelperRequest.Apply): String {
        val resolved = resolve(FilterPlan.names(request.hosts), APPLY_RESOLVE_MILLIS)
        return synchronized(lock) {
            val next = HelperState(request, state.expiredSessionId.takeUnless { it == request.sessionId })
            if (!filters.replace(FilterPlan.extend(emptySet(), resolved)) || !save(next)) return@synchronized "error\tapply"
            ender.endMatching(request.executables)
            "ok"
        }
    }

    /** A failed pass, such as a full disk, is retried on the next tick; the scheduled task must never end. */
    private fun tick() {
        runCatching {
            synchronized(lock) {
                val applied = state.applied ?: return@runCatching
                if (now() >= applied.endEpochMillis) {
                    if (filters.replace(emptySet())) save(HelperState(null, applied.sessionId))
                } else {
                    ender.endMatching(applied.executables)
                }
            }
        }
    }

    /** Every minute during a pause: addresses that the names now resolve to are added, and none are removed. */
    private fun refresh() {
        runCatching {
            val applied = synchronized(lock) { state.applied } ?: return@runCatching
            val resolved = resolve(FilterPlan.names(applied.hosts), REFRESH_RESOLVE_MILLIS)
            synchronized(lock) {
                if (state.applied?.sessionId == applied.sessionId && now() < applied.endEpochMillis) {
                    filters.replace(FilterPlan.extend(filters.blocked, resolved))
                }
            }
        }
    }

    /** Resolves [names] in parallel through the system resolver; a name not answered in time waits for the next pass. */
    private fun resolve(
        names: List<String>,
        timeoutMillis: Long,
    ): List<InetAddress> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val lookups: List<Future<List<InetAddress>>> = names.map { name ->
            resolvers.submit<List<InetAddress>> {
                try {
                    InetAddress.getAllByName(name).toList()
                } catch (_: UnknownHostException) {
                    emptyList()
                }
            }
        }
        return lookups.flatMap { lookup ->
            try {
                lookup.get((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
            } catch (_: TimeoutException) {
                lookup.cancel(true)
                emptyList()
            } catch (_: ExecutionException) {
                emptyList()
            }
        }
    }

    private fun save(next: HelperState): Boolean {
        if (!folder.write(folder.state, next.encode())) return false
        state = next
        return true
    }

    private fun daemon(name: String) = ThreadFactory { task -> Thread(task, name).apply { isDaemon = true } }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val REFRESH_MILLIS = 60_000L
        const val APPLY_RESOLVE_MILLIS = 3_000L
        const val REFRESH_RESOLVE_MILLIS = 30_000L
        const val RESOLVER_THREADS = 16
        const val CONNECTION_WORKERS = 4
    }
}
