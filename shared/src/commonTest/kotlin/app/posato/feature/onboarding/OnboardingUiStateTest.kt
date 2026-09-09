package app.posato.feature.onboarding

import app.posato.feature.onboarding.data.LocalSetupFailure
import app.posato.feature.onboarding.data.LocalSetupResult
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.onboarding.data.SetupCompletion
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.ui.message
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import app.posato.feature.targets.ui.WebsiteBatchReceipt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingUiStateTest {
    @Test
    fun `given a fresh holder when advancing then the six steps run in order and stop at summary`() = runTest {
        val holder = OnboardingUiState(
            FakeSetupStore(),
            FakeTargetPolicyStore(),
            FakeApplicationAccess(),
            FakeMacHelper(),
            this,
        )

        val visited = mutableListOf(holder.step)
        repeat(7) {
            holder.advance()
            visited.add(holder.step)
        }

        assertEquals(
            listOf(
                OnboardingStep.PURPOSE,
                OnboardingStep.PRIVACY,
                OnboardingStep.ICLOUD,
                OnboardingStep.PERMISSION,
                OnboardingStep.WEBSITE,
                OnboardingStep.SUMMARY,
                OnboardingStep.SUMMARY,
                OnboardingStep.SUMMARY,
            ),
            visited,
        )
    }

    @Test
    fun `given no step action when moving through the flow then no provider is touched`() = runTest {
        val setup = FakeSetupStore()
        val policy = FakeTargetPolicyStore()
        val access = FakeApplicationAccess()
        val helper = FakeMacHelper()
        val holder = OnboardingUiState(setup, policy, access, helper, this)

        holder.loadCompletion()
        repeat(5) { holder.advance() }
        runCurrent()

        assertEquals(0, access.calls)
        assertTrue(helper.calls.isEmpty())
        assertEquals(0, policy.reads)
        assertTrue(policy.replaced.isEmpty())
        assertEquals(1, setup.reads)
        assertEquals(0, setup.completed)
    }

    @Test
    fun `given every sync status when messaged then each maps to a distinct string`() {
        val messages = SyncStatus.entries.map { status -> status.message(false) }

        assertEquals(SyncStatus.entries.size, messages.toSet().size)
    }

    @Test
    fun `given each authorization answer when requested then the read back access is kept`() = runTest {
        val answers = listOf(
            ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.READY),
            ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.AUTHORIZATION_DENIED),
            ApplicationAccessResult.Unavailable,
            ApplicationAccessResult.Failed,
        )

        answers.forEach { answer ->
            val access = FakeApplicationAccess(answer)
            val holder = OnboardingUiState(FakeSetupStore(), FakeTargetPolicyStore(), access, FakeMacHelper(), this)

            holder.requestAccess()
            runCurrent()

            assertEquals(answer, holder.accessResult)
        }
    }

    @Test
    fun `given a running request when a second action arrives then it is ignored`() = runTest {
        val gate = CompletableDeferred<ApplicationAccessResult>()
        val access = FakeApplicationAccess(gate = gate)
        val helper = FakeMacHelper()
        val holder = OnboardingUiState(FakeSetupStore(), FakeTargetPolicyStore(), access, helper, this)

        holder.requestAccess()
        holder.requestAccess()
        holder.enableHelper()
        runCurrent()

        assertEquals(1, access.calls)
        assertTrue(helper.calls.isEmpty())
        gate.complete(ApplicationAccessResult.Unavailable)
        runCurrent()

        assertEquals(ApplicationAccessResult.Unavailable, holder.accessResult)
    }

    @Test
    fun `given each helper answer when enabled or rechecked then the readiness is kept`() = runTest {
        val answers = listOf(
            MacHelperReadiness.READY,
            MacHelperReadiness.APPROVAL_REQUIRED,
            MacHelperReadiness.UNAVAILABLE,
        )

        answers.forEach { answer ->
            val helper = FakeMacHelper(answer)
            val holder = OnboardingUiState(FakeSetupStore(), FakeTargetPolicyStore(), FakeApplicationAccess(), helper, this)

            holder.enableHelper()
            runCurrent()

            assertEquals(answer, holder.helperReadiness)
            assertEquals(listOf("enable"), helper.calls)
        }
    }

    @Test
    fun `given a helper answer when rechecked then only status runs`() = runTest {
        val helper = FakeMacHelper(MacHelperReadiness.READY)
        val holder = OnboardingUiState(FakeSetupStore(), FakeTargetPolicyStore(), FakeApplicationAccess(), helper, this)

        holder.recheckHelper()
        runCurrent()

        assertEquals(MacHelperReadiness.READY, holder.helperReadiness)
        assertEquals(listOf("recheck"), helper.calls)
    }

    @Test
    fun `given a website draft when submitted then the domain persists and the receipt confirms`() = runTest {
        val policy = FakeTargetPolicyStore()
        val holder = OnboardingUiState(FakeSetupStore(), policy, FakeApplicationAccess(), FakeMacHelper(), this)
        var receipt: WebsiteBatchReceipt? = null

        holder.submitWebsites("example.com", 1) { receipt = it }
        runCurrent()

        assertEquals(listOf("example.com"), policy.replaced.single().domains.map { it.canonicalValue })
        assertEquals(1, holder.savedWebsites)
        val saved = assertIs<WebsiteBatchReceipt>(receipt)
        assertTrue(saved.saved)
        assertEquals(1, saved.addedCount)
    }

    @Test
    fun `given a duplicate website when submitted then nothing persists twice`() = runTest {
        val policy = FakeTargetPolicyStore()
        val holder = OnboardingUiState(FakeSetupStore(), policy, FakeApplicationAccess(), FakeMacHelper(), this)

        holder.submitWebsites("example.com", 1) { }
        runCurrent()
        holder.submitWebsites("example.com", 2) { }
        runCurrent()

        assertEquals(1, policy.replaced.size)
        assertEquals(1, holder.savedWebsites)
    }

    @Test
    fun `given an unreadable policy when submitted then the receipt reports failure`() = runTest {
        val holder = OnboardingUiState(
            FakeSetupStore(),
            FailingTargetPolicyStore(),
            FakeApplicationAccess(),
            FakeMacHelper(),
            this,
        )
        var receipt: WebsiteBatchReceipt? = null

        holder.submitWebsites("example.com", 1) { receipt = it }
        runCurrent()

        assertEquals(false, assertIs<WebsiteBatchReceipt>(receipt).saved)
        assertEquals(0, holder.savedWebsites)
    }

    @Test
    fun `given each stored completion when read then the tri-state is reported`() = runTest {
        val completions = mapOf(
            SetupCompletion.COMPLETE to SetupCompletion.COMPLETE,
            SetupCompletion.INCOMPLETE to SetupCompletion.INCOMPLETE,
        )

        completions.forEach { (stored, expected) ->
            val holder = OnboardingUiState(
                FakeSetupStore(stored),
                FakeTargetPolicyStore(),
                FakeApplicationAccess(),
                FakeMacHelper(),
                this,
            )

            assertNull(holder.completion)
            holder.loadCompletion()
            runCurrent()

            assertEquals(expected, holder.completion)
        }
    }

    @Test
    fun `given an unreadable store when read then the completion is unknown`() = runTest {
        val holder = OnboardingUiState(
            FailingSetupStore(),
            FakeTargetPolicyStore(),
            FakeApplicationAccess(),
            FakeMacHelper(),
            this,
        )

        holder.loadCompletion()
        runCurrent()

        assertEquals(SetupCompletion.UNKNOWN, holder.completion)
    }

    @Test
    fun `given the summary when finished then completion persists once and the flow closes`() = runTest {
        val setup = FakeSetupStore()
        val holder = OnboardingUiState(setup, FakeTargetPolicyStore(), FakeApplicationAccess(), FakeMacHelper(), this)
        var finished = 0

        holder.finish { finished++ }
        holder.finish { finished++ }
        runCurrent()

        assertEquals(1, setup.completed)
        assertEquals(1, finished)
    }

    @Test
    fun `given a failing completion write when finished then the flow still closes`() = runTest {
        val holder = OnboardingUiState(
            FailingSetupStore(),
            FakeTargetPolicyStore(),
            FakeApplicationAccess(),
            FakeMacHelper(),
            this,
        )
        var finished = 0

        holder.finish { finished++ }
        runCurrent()

        assertEquals(1, finished)
    }
}

private class FakeSetupStore(
    private val stored: SetupCompletion = SetupCompletion.INCOMPLETE,
) : LocalSetupStore {
    var reads = 0
    var completed = 0

    override suspend fun read(): LocalSetupResult<SetupCompletion> {
        reads++
        return LocalSetupResult.Success(stored)
    }

    override suspend fun markComplete(): LocalSetupResult<Unit> {
        completed++
        return LocalSetupResult.Success(Unit)
    }
}

private class FailingSetupStore : LocalSetupStore {
    override suspend fun read(): LocalSetupResult<SetupCompletion> {
        return LocalSetupResult.Failure(LocalSetupFailure.STORAGE_FAILURE)
    }

    override suspend fun markComplete(): LocalSetupResult<Unit> {
        return LocalSetupResult.Failure(LocalSetupFailure.STORAGE_FAILURE)
    }
}

private class FakeTargetPolicyStore : LocalTargetPolicyStore {
    var reads = 0
    val replaced = mutableListOf<TargetPolicy>()
    private var revision = 0L
    private var policy = (TargetPolicy.fromStoredValues(emptyList(), null) as TargetPolicyValidationResult.Success).policy

    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        reads++
        return LocalPolicyResult.Success(LocalTargetPolicyState(revision, policy))
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        replaced.add(policy)
        revision = expectedRevision + 1
        this.policy = policy
        return LocalPolicyResult.Success(LocalTargetPolicyState(revision, policy))
    }
}

private class FailingTargetPolicyStore : LocalTargetPolicyStore {
    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
    }
}

private class FakeApplicationAccess(
    private val answer: ApplicationAccessResult = ApplicationAccessResult.Unavailable,
    private val gate: CompletableDeferred<ApplicationAccessResult>? = null,
) : ApplicationAccessPort {
    var calls = 0

    override suspend fun requestAuthorization(): ApplicationAccessResult {
        calls++
        return gate?.await() ?: answer
    }
}

private class FakeMacHelper(
    private val readiness: MacHelperReadiness = MacHelperReadiness.UNAVAILABLE,
) : MacHelperPort {
    val calls = mutableListOf<String>()

    override suspend fun enable(): MacHelperReadiness {
        calls.add("enable")
        return readiness
    }

    override suspend fun recheck(): MacHelperReadiness {
        calls.add("recheck")
        return readiness
    }

    override fun openApprovalSettings() {
        calls.add("settings")
    }
}
