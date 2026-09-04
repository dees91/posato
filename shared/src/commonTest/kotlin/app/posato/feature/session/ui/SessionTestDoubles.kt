package app.posato.feature.session.ui

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
