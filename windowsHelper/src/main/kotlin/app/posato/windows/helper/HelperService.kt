package app.posato.windows.helper

import java.net.InetAddress
import java.net.UnknownHostException
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The service of ADR 0010 on Windows. It answers one request per connection from the installing user (or the
 * system), keeps the block filters and the state file in step, resolves the paused names again every minute and only
 * adds addresses, ends the chosen programs while a pause runs, and clears a pause by itself at its end. Each second it
 * follows the window plans of the last apply on the wall clock (TARGETS-013), with the application gone; [BlockPlan]
 * keeps the addresses per host and discards stale resolutions. It never reads traffic and never logs hosts or plans.
 */
internal class HelperService(
    private val folder: HelperFolder,
    config: HelperConfig,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ::currentSystemZone,
) {
    private val lock = Any()
    private val filters = WfpFilters(config.sid)
    private val ender = ProcessEnder(config.sid)

    /** The one-second tick has a thread of its own, so a slow lookup never delays a window change or the end. */
    private val ticker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemon("posato-helper-tick"))

    /** Refreshes run one at a time on their own thread; an extra one waits for the running one, at most one queued. */
    private val refresher: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemon("posato-helper-refresh"))
    private val refreshQueued = AtomicBoolean(false)
    private val resolvers = Executors.newFixedThreadPool(RESOLVER_THREADS, daemon("posato-helper-resolver"))
    private val workers = Executors.newFixedThreadPool(CONNECTION_WORKERS, daemon("posato-helper-connection"))
    private val server = PipeServer(config.sid, workers, ::answer)
    private var state = folder.read(folder.state)?.let(HelperState::decode) ?: HelperState.EMPTY

    /** After a restart the saved apply has no addresses yet; the first refresh resolves them for the hosts blocked now. */
    private var plan = state.applied?.let { applied -> BlockPlan.empty(zone).applied(applied, emptyMap(), now()) } ?: BlockPlan.empty(zone)

    /** Serves until [stop]; false when the pipe name is already taken, so the service should fail and restart. */
    fun serve(): Boolean {
        ticker.scheduleWithFixedDelay(::tick, 0, TICK_MILLIS, TimeUnit.MILLISECONDS)
        refresher.scheduleWithFixedDelay(::refresh, 0, REFRESH_MILLIS, TimeUnit.MILLISECONDS)
        return server.serve()
    }

    /** Stops serving; closing the filtering session removes every filter, and a restart applies the state again. */
    fun stop() {
        server.stop()
        ticker.shutdownNow()
        refresher.shutdownNow()
        resolvers.shutdownNow()
        workers.shutdownNow()
        synchronized(lock) { filters.close() }
    }

    fun handle(request: HelperRequest): String {
        return when (request) {
            is HelperRequest.Apply -> apply(request)

            HelperRequest.Clear -> synchronized(lock) {
                plan = plan.cleared()
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

    /**
     * Every held host is resolved before the lock is taken, so the old filters hold until the new ones replace them,
     * and a host open in its window already has its addresses when the window closes.
     */
    private fun apply(request: HelperRequest.Apply): String {
        val resolved = resolve(request.hosts, APPLY_RESOLVE_MILLIS)
        return synchronized(lock) {
            val next = HelperState(request, state.expiredSessionId.takeUnless { it == request.sessionId })
            val nextPlan = plan.applied(request, resolved, now())
            if (!filters.replace(nextPlan.filters()) || !save(next)) return@synchronized "error\tapply"
            plan = nextPlan
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
                    if (filters.replace(emptySet())) {
                        plan = plan.cleared()
                        save(HelperState(null, applied.sessionId))
                    }
                } else {
                    // A host that becomes blocked is resolved again soon instead of at the next minute.
                    val before = plan.blockedHosts().toSet()
                    if (plan.evaluate(now()) && plan.blockedHosts().any { it !in before }) requestRefresh()
                    val wanted = plan.filters()
                    if (wanted != filters.blocked) filters.replace(wanted)
                    ender.endMatching(applied.executables)
                }
            }
        }
    }

    /**
     * Every minute during a pause, and at once when a host becomes blocked: addresses that the hosts blocked now
     * resolve to are added, and none are removed. A result that completes after a new apply or a window boundary is
     * discarded; the next pass resolves again.
     */
    private fun refresh() {
        runCatching {
            val (hosts, ticket) = synchronized(lock) { plan.blockedHosts() to plan.ticket() }
            if (hosts.isEmpty()) return@runCatching
            val resolved = resolve(hosts, REFRESH_RESOLVE_MILLIS)
            synchronized(lock) {
                if (plan.merge(ticket, resolved, now())) {
                    val wanted = plan.filters()
                    if (wanted != filters.blocked) filters.replace(wanted)
                }
            }
        }
    }

    private fun requestRefresh() {
        if (!refreshQueued.getAndSet(true)) {
            refresher.execute {
                refreshQueued.set(false)
                refresh()
            }
        }
    }

    /**
     * Resolves each host and its `www` name in parallel through the system resolver; a name not answered in time
     * waits for the next pass.
     */
    private fun resolve(
        hosts: List<String>,
        timeoutMillis: Long,
    ): Map<String, List<InetAddress>> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val lookups: List<Pair<String, Future<List<InetAddress>>>> = hosts.distinct().flatMap { host ->
            FilterPlan.names(listOf(host)).map { name ->
                host to resolvers.submit<List<InetAddress>> {
                    try {
                        InetAddress.getAllByName(name).toList()
                    } catch (_: UnknownHostException) {
                        emptyList()
                    }
                }
            }
        }
        return lookups.groupBy({ it.first }) { (_, lookup) ->
            try {
                lookup.get((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
            } catch (_: TimeoutException) {
                lookup.cancel(true)
                emptyList()
            } catch (_: ExecutionException) {
                emptyList()
            }
        }.mapValues { (_, results) -> results.flatten() }
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

/**
 * The zone Windows uses now. The JVM keeps the zone it read at start, so the cached default is dropped and read from
 * the system again at each call; a time-zone change then applies within a second, without a restart.
 */
internal fun currentSystemZone(): ZoneId {
    return synchronized(TimeZone::class.java) {
        System.clearProperty("user.timezone")
        TimeZone.setDefault(null)
        TimeZone.getDefault().toZoneId()
    }
}
