package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.PauseItems
import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.enforcement.RunningPartItems
import app.posato.feature.enforcement.devicePauseLimits
import app.posato.feature.enforcement.planPause
import app.posato.feature.session.data.PART_SESSION
import app.posato.feature.session.data.PartRetentionStore
import app.posato.feature.session.data.RetainedItems
import app.posato.feature.session.data.RetainedPart
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The manual session as one running part: its set's websites now plus every website it already paused,
 * within the device's limits. What it pauses is recorded before it is applied, so a website removed from the
 * set while the session runs stays paused until the session ends.
 */
@SingleIn(AppScope::class)
internal class SessionComposition(
    private val retention: PartRetentionStore?,
    private val limits: PauseLimits,
    private val loadTargets: suspend (PauseSetId) -> SessionTargetsState,
) {
    @Inject
    constructor(
        retention: SqlPartRetentionStore,
        policyStore: LocalTargetPolicyStore,
        applicationMappings: LocalApplicationMappings,
    ) : this(retention, devicePauseLimits, { setId -> loadSessionTargets(policyStore, applicationMappings, setId) })

    private var last: Pair<SessionId, List<String>>? = null

    /** Whether the session's composition changed since it was last applied, such as after a set edit. */
    suspend fun differs(record: SessionRecord): Boolean {
        val previous = last ?: return false
        val now = compose(record).policy?.domains?.map { domain -> domain.canonicalValue }.orEmpty()
        return previous.first != record.sessionId || previous.second != now
    }

    suspend fun compose(record: SessionRecord): SessionTargetsState {
        val targets = loadTargets(record.setId)
        val policy = targets.policy ?: return targets
        val part = RetainedPart(PART_SESSION, record.sessionId.value.copyBytes())
        val retained = retention?.read(part) ?: RetainedItems()
        val mappingIds = (targets.mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings.orEmpty()
            .mapTo(mutableSetOf()) { mapping -> mapping.id.canonicalValue }
        val plan = planPause(
            listOf(
                RunningPartItems(
                    partId = PART_SESSION,
                    startEpochMillis = record.startEpochMillis,
                    endEpochMillis = record.endEpochMillis,
                    current = PauseItems(policy.domains.mapTo(mutableSetOf()) { domain -> domain.canonicalValue }, mappingIds),
                    retained = PauseItems(retained.domains),
                ),
            ),
            limits,
        )
        retention?.hold(part, RetainedItems(plan.items.domains))
        val domains = plan.items.domains.sorted()
        last = record.sessionId to domains
        val composed = TargetPolicy.fromStoredValues(domains, policy.applicationPolicyName?.canonicalValue)
        return targets.copy(policy = (composed as? TargetPolicyValidationResult.Success)?.policy ?: policy)
    }
}

/** Applies the running session again when a set edit changed what it pauses; an unchanged edit touches nothing. */
internal suspend fun SessionTransitionOwner.recompose(composition: SessionComposition) {
    val active = status.value as? LocalSessionStatus.Active ?: return
    if (view.value.busy || view.value.state !is EnforcementState.Active) {
        return
    }
    if (composition.differs(active.record)) {
        retry()
    }
}
