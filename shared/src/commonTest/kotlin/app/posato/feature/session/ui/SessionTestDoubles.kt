package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
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
import app.posato.feature.targets.domain.TargetPolicy

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
    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return result
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        throw UnsupportedOperationException()
    }
}

internal class FakeEnforcementPort(
    var applyReport: EnforcementApplyReport = EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false),
    var clearOutcome: EnforcementOutcome = EnforcementOutcome.CLEARED,
    var statusOutcome: EnforcementOutcome = EnforcementOutcome.APPLIED,
    var statusSequence: ArrayDeque<EnforcementOutcome>? = null,
    var expiredSessionIds: Set<String> = emptySet(),
    override val reapplyRequiresPrompt: Boolean = false,
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
        return clearOutcome
    }

    override suspend fun status(): EnforcementOutcome {
        calls += "status"
        val next = statusSequence?.removeFirstOrNull()
        return next ?: statusOutcome
    }

    override suspend fun pollSuspendedExpiry(sessionId: String): Boolean {
        calls += "poll"
        val expired = sessionId in expiredSessionIds
        expiredSessionIds -= sessionId
        return expired
    }
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
