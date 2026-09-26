package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.ExpiryDisplacement
import app.posato.feature.enforcement.ExpiryDisplacementOutcome
import app.posato.feature.session.data.SessionSyncTriggers
import app.posato.feature.session.data.SessionWorkspaceCapture
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationRemovalResult
import app.posato.feature.targets.data.LocalApplicationSelectionResult
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeSessionIdGenerator : SessionIdGenerator {
    private var next: Int = 1

    override fun create(): SessionId {
        return SessionId(testIdentifier(next++))
    }
}

internal class FakeSessionTimeFormat : SessionTimeFormat {
    override fun formatTime(
        epochMillis: Long,
        nowEpochMillis: Long,
    ): String {
        return "formatted-$epochMillis"
    }
}

internal class FakeSessionPolicyStore(
    var result: LocalPolicyResult<LocalTargetPolicyState>,
) : LocalTargetPolicyStore {
    override suspend fun <T> withWriteGate(block: suspend () -> T): T {
        return block()
    }

    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val policyChanges: Flow<Unit>
        get() = changes

    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return result
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
        syncWrite: PolicySyncWrite?,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        throw UnsupportedOperationException()
    }
}

internal class FakeEnforcementPort(
    var applyReport: EnforcementApplyReport = EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false),
    var clearOutcome: EnforcementOutcome = EnforcementOutcome.CLEARED,
    var statusOutcome: EnforcementOutcome = EnforcementOutcome.APPLIED,
    var statusError: Exception? = null,
    var statusSequence: ArrayDeque<EnforcementOutcome>? = null,
    var expiredSessionIds: Set<String> = emptySet(),
    var heldSessionIds: Set<String> = emptySet(),
    override val reapplyRequiresPrompt: Boolean = false,
    var clearHook: (() -> Unit)? = null,
    var clearGate: CompletableDeferred<Unit>? = null,
) : EnforcementPort {
    val calls = mutableListOf<String>()
    var lastRequest: EnforcementRequest? = null

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        calls += "apply"
        lastRequest = request
        return applyReport
    }

    override suspend fun clear(): EnforcementOutcome {
        calls += "clear"
        clearHook?.invoke()
        clearGate?.await()
        return clearOutcome
    }

    override suspend fun status(): EnforcementOutcome {
        calls += "status"
        statusError?.let { throw it }
        val next = statusSequence?.removeFirstOrNull()
        return next ?: statusOutcome
    }

    override suspend fun peekSuspendedExpiry(sessionId: String): Boolean {
        calls += "peek"
        return sessionId in expiredSessionIds
    }

    override suspend fun holdsSession(sessionId: String): Boolean {
        calls += "holds"
        return sessionId in heldSessionIds
    }

    var displacedSessionId: String? = null

    override suspend fun displacedSuspendedExpiry(currentSessionId: String): ExpiryDisplacement {
        calls += "displace"
        val displaced = displacedSessionId ?: expiredSessionIds.firstOrNull()
        return ExpiryDisplacement(
            if (displaced == null) ExpiryDisplacementOutcome.ABSENT else ExpiryDisplacementOutcome.PRESENT,
            displaced,
        )
    }

    var acknowledgeError: Exception? = null

    override suspend fun acknowledgeSuspendedExpiry(sessionId: String): Boolean {
        calls += "acknowledge"
        acknowledgeError?.let { throw it }
        acknowledgedSessionIds += sessionId
        if (displacedSessionId == sessionId) displacedSessionId = null
        val expired = sessionId in expiredSessionIds
        expiredSessionIds -= sessionId
        return expired
    }

    val acknowledgedSessionIds = mutableListOf<String>()
}

internal class FakeSessionSyncTriggers(
    var capture: SessionWorkspaceCapture = SessionWorkspaceCapture.Unlinked,
) : SessionSyncTriggers {
    var syncRequests: Int = 0

    override suspend fun captureWorkspace(): SessionWorkspaceCapture {
        return capture
    }

    override fun requestSync() {
        syncRequests += 1
    }
}

internal fun sessionOwnerOf(
    store: FakeLocalSessionStore,
    enforcement: FakeEnforcementPort,
    clock: FakeSessionClock,
    policyStore: LocalTargetPolicyStore,
    mappings: LocalApplicationMappings,
    triggers: FakeSessionSyncTriggers = FakeSessionSyncTriggers(),
    dispatcher: CoroutineDispatcher,
): SessionTransitionOwner {
    return SessionTransitionOwner(
        backgroundDispatcher = dispatcher,
        store = store,
        clock = clock,
        enforcement = enforcement,
        loadTargets = { loadSessionTargets(policyStore, mappings) },
        triggers = triggers,
    )
}

internal class FakeSessionMappings(
    var result: LocalApplicationMappingsLoadResult = LocalApplicationMappingsLoadResult.Unavailable(),
) : LocalApplicationMappings {
    override suspend fun load(): LocalApplicationMappingsLoadResult {
        return result
    }

    override suspend fun chooseApplications(): LocalApplicationSelectionResult {
        return LocalApplicationSelectionResult.Unavailable
    }

    override suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult {
        return LocalApplicationRemovalResult.Unavailable
    }

    override suspend fun clear(): LocalApplicationRemovalResult {
        return LocalApplicationRemovalResult.Unavailable
    }
}
