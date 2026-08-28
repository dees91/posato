package app.posato.feature.targets.ui

import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetsViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given uiState without a collector when ViewModel is created then storage is not read`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(0))
        TargetsViewModel(store)
        scheduler.runCurrent()

        assertEquals(0, store.readCalls)
    }

    @Test
    fun `given collection restarts after timeout when state is observed then storage is read again`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(0))
        val viewModel = TargetsViewModel(store)
        val collection = observe(viewModel)
        scheduler.runCurrent()
        collection.cancel()

        advanceTimeBy(5_001)
        scheduler.runCurrent()
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals(2, store.readCalls)
    }

    @Test
    fun `given corrupted storage when retry succeeds then failure is replaced by the persisted snapshot`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(7, domains = listOf("stable.example")))
        store.nextReadFailure = LocalPolicyFailure.CORRUPTION
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals(TargetsOperationFailure.CORRUPTED_POLICY, viewModel.uiState.value.operationFailure)
        assertFalse(viewModel.uiState.value.hasLoaded)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.retry()
        scheduler.runCurrent()

        assertEquals(null, viewModel.uiState.value.operationFailure)
        assertEquals(listOf("stable.example"), viewModel.uiState.value.domains)
    }

    @Test
    fun `given an empty policy when domain and application group change then persisted snapshots drive both sections`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(0))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.submitDomain("EXAMPLE.COM")
        scheduler.runCurrent()
        viewModel.submitApplicationPolicy("  Cafe\u0301  ")
        scheduler.runCurrent()

        assertEquals(listOf("example.com"), viewModel.uiState.value.domains)
        assertEquals("Caf\u00E9", viewModel.uiState.value.applicationPolicyName)

        viewModel.beginEditingApplicationPolicy()
        scheduler.runCurrent()
        viewModel.submitApplicationPolicy("Work tools")
        scheduler.runCurrent()
        viewModel.removeApplicationPolicy()
        scheduler.runCurrent()

        assertEquals(null, viewModel.uiState.value.applicationPolicyName)
        assertEquals(listOf("example.com"), viewModel.uiState.value.domains)
        assertEquals(4, store.replaceCalls)
    }

    @Test
    fun `given invalid application input when submitted then the saved aggregate and domain editor remain unchanged`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(2, domains = listOf("stable.example"), applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingDomain("stable.example")
        scheduler.runCurrent()
        val domainSession = viewModel.uiState.value.domainEditorSession

        viewModel.beginEditingApplicationPolicy()
        scheduler.runCurrent()
        viewModel.submitApplicationPolicy("line\nfeed")
        scheduler.runCurrent()

        assertEquals(ApplicationPolicyEntryFailure.INVALID_CHARACTERS, viewModel.uiState.value.applicationPolicyInputFailure)
        assertEquals("Social feeds", viewModel.uiState.value.applicationPolicyName)
        assertEquals("stable.example", viewModel.uiState.value.editingDomain)
        assertEquals(domainSession, viewModel.uiState.value.domainEditorSession)
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun `given invalid application input when corrected replacement conflicts then entry failure is cleared`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(2, applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingApplicationPolicy()
        scheduler.runCurrent()
        viewModel.submitApplicationPolicy("line\nfeed")
        scheduler.runCurrent()
        assertEquals(ApplicationPolicyEntryFailure.INVALID_CHARACTERS, viewModel.uiState.value.applicationPolicyInputFailure)
        store.replaceExternally(applicationPolicyName = "Social feeds")

        viewModel.submitApplicationPolicy("Work tools")
        scheduler.runCurrent()

        assertEquals(null, viewModel.uiState.value.applicationPolicyInputFailure)
        assertEquals(TargetsOperationFailure.REVISION_CONFLICT, viewModel.uiState.value.operationFailure)
        assertEquals(true, viewModel.uiState.value.isEditingApplicationPolicy)
        assertEquals("Social feeds", viewModel.uiState.value.applicationPolicyName)
        assertEquals(1, store.replaceCalls)
    }

    @Test
    fun `given a canonical domain duplicate when submitted then it is rejected without replacing storage`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(4, domains = listOf("example.com")))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.submitDomain("EXAMPLE.COM.")
        scheduler.runCurrent()

        assertEquals(ExactDomainEntryFailure.DUPLICATE, viewModel.uiState.value.domainInputFailure)
        assertEquals(listOf("example.com"), viewModel.uiState.value.domains)
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun `given the domain limit when another domain is submitted then the limit error is shown without replacing storage`() = runTest(dispatcher) {
        val domains = List(ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) { index -> "domain$index.example" }
        val store = FakeTargetPolicyStore(stateOf(4, domains = domains, applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.submitDomain("overflow.example")
        scheduler.runCurrent()

        assertEquals(ExactDomainEntryFailure.LIMIT_REACHED, viewModel.uiState.value.domainInputFailure)
        assertEquals(null, viewModel.uiState.value.operationFailure)
        assertEquals(domains.sorted(), viewModel.uiState.value.domains)
        assertEquals("Social feeds", viewModel.uiState.value.applicationPolicyName)
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun `given an invalid domain edit when submitted then the previous valid row remains`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(2, domains = listOf("stable.example")))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingDomain("stable.example")
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.domainEditorSession

        viewModel.submitDomain("https://invalid.example")
        scheduler.runCurrent()

        assertEquals(ExactDomainEntryFailure.INVALID_DOMAIN, viewModel.uiState.value.domainInputFailure)
        assertEquals(editorSession, viewModel.uiState.value.domainEditorSession)
        assertEquals("stable.example", viewModel.uiState.value.editingDomain)
        assertEquals(listOf("stable.example"), viewModel.uiState.value.domains)
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun `given an edited domain was removed externally when conflict reloads then the editor is reset`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(3, domains = listOf("stable.example")))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingDomain("stable.example")
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.domainEditorSession
        store.replaceExternally(domains = listOf("other.example"))

        viewModel.submitDomain("replacement.example")
        scheduler.runCurrent()
        viewModel.retry()
        scheduler.runCurrent()

        assertEquals(listOf("other.example"), viewModel.uiState.value.domains)
        assertEquals(null, viewModel.uiState.value.editingDomain)
        assertEquals(editorSession + 1, viewModel.uiState.value.domainEditorSession)
        assertEquals(null, viewModel.uiState.value.operationFailure)
    }

    @Test
    fun `given an edited domain remains externally when conflict reloads then the editor is preserved`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(3, domains = listOf("stable.example")))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingDomain("stable.example")
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.domainEditorSession
        store.replaceExternally(domains = listOf("stable.example", "other.example"))

        viewModel.submitDomain("replacement.example")
        scheduler.runCurrent()
        viewModel.retry()
        scheduler.runCurrent()

        assertEquals(listOf("other.example", "stable.example"), viewModel.uiState.value.domains)
        assertEquals("stable.example", viewModel.uiState.value.editingDomain)
        assertEquals(editorSession, viewModel.uiState.value.domainEditorSession)
        assertEquals(null, viewModel.uiState.value.operationFailure)
    }

    @Test
    fun `given an active domain edit when cancelled then a new empty editor session is exposed`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(1, domains = listOf("stable.example")))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingDomain("stable.example")
        scheduler.runCurrent()
        val editingSession = viewModel.uiState.value.domainEditorSession

        viewModel.cancelEditingDomain()
        scheduler.runCurrent()

        assertEquals(editingSession + 1, viewModel.uiState.value.domainEditorSession)
        assertEquals(null, viewModel.uiState.value.editingDomain)
    }

    @Test
    fun `given an active application edit when cancelled then the saved group remains visible`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingApplicationPolicy()
        scheduler.runCurrent()
        val editingSession = viewModel.uiState.value.applicationEditorSession

        viewModel.cancelEditingApplicationPolicy()
        scheduler.runCurrent()

        assertEquals(editingSession + 1, viewModel.uiState.value.applicationEditorSession)
        assertFalse(viewModel.uiState.value.isEditingApplicationPolicy)
        assertEquals("Social feeds", viewModel.uiState.value.applicationPolicyName)
    }

    @Test
    fun `given a group edit when conflict reload keeps the same original then the editor is preserved`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(3, applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingApplicationPolicy()
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.applicationEditorSession
        store.replaceExternally(applicationPolicyName = "Social feeds")

        viewModel.submitApplicationPolicy("New group")
        scheduler.runCurrent()
        viewModel.retry()
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isEditingApplicationPolicy)
        assertEquals(editorSession, viewModel.uiState.value.applicationEditorSession)
        assertEquals("Social feeds", viewModel.uiState.value.applicationPolicyName)
    }

    @Test
    fun `given a group edit when conflict reload changes the original then the editor is reset`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(3, applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditingApplicationPolicy()
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.applicationEditorSession
        store.replaceExternally(applicationPolicyName = "Work tools")

        viewModel.submitApplicationPolicy("New group")
        scheduler.runCurrent()
        viewModel.retry()
        scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isEditingApplicationPolicy)
        assertEquals(editorSession + 1, viewModel.uiState.value.applicationEditorSession)
        assertEquals("Work tools", viewModel.uiState.value.applicationPolicyName)
    }

    @Test
    fun `given a conflict when mutations are attempted then a successful reload is required`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        store.replaceExternally(applicationPolicyName = "Social feeds")
        viewModel.submitApplicationPolicy("Work tools")
        scheduler.runCurrent()

        val applicationEditorSession = viewModel.uiState.value.applicationEditorSession
        viewModel.beginEditingApplicationPolicy()
        viewModel.removeApplicationPolicy()
        viewModel.submitDomain("blocked.example")
        scheduler.runCurrent()

        assertEquals(TargetsOperationFailure.REVISION_CONFLICT, viewModel.uiState.value.operationFailure)
        assertFalse(viewModel.uiState.value.isEditingApplicationPolicy)
        assertEquals(applicationEditorSession, viewModel.uiState.value.applicationEditorSession)
        assertEquals(1, store.replaceCalls)
        val suspendedRead = store.suspendNextRead()

        viewModel.retry()
        scheduler.runCurrent()
        viewModel.submitDomain("example.com")
        viewModel.removeApplicationPolicy()
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(1, store.replaceCalls)
        suspendedRead.complete(Unit)
        scheduler.runCurrent()
        viewModel.beginEditingApplicationPolicy()
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isEditingApplicationPolicy)
        assertEquals(null, viewModel.uiState.value.operationFailure)
        viewModel.submitDomain("example.com")
        scheduler.runCurrent()
        assertEquals(2, store.replaceCalls)
    }

    @Test
    fun `given cancellation when saving then valid state is not replaced or left busy`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Social feeds"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        store.cancelNextReplace = true

        viewModel.submitApplicationPolicy("Work tools")
        scheduler.runCurrent()

        assertEquals("Social feeds", viewModel.uiState.value.applicationPolicyName)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `given cancellation when saving a domain then valid state is not replaced or left busy`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(1, domains = listOf("stable.example")))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        store.cancelNextReplace = true

        viewModel.submitDomain("new.example")
        scheduler.runCurrent()

        assertEquals(listOf("stable.example"), viewModel.uiState.value.domains)
        assertEquals(0L, viewModel.uiState.value.domainEditorSession)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `given state with private values when rendered as text then all values remain redacted`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(1, listOf("private.example"), "Private apps"))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals("TargetsUiState(redacted)", viewModel.uiState.value.toString())
    }
}

private fun TestScope.observe(viewModel: TargetsViewModel): Job {
    return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
}

private class FakeTargetPolicyStore(
    private var state: LocalTargetPolicyState,
) : LocalTargetPolicyStore {
    var readCalls: Int = 0
    var replaceCalls: Int = 0
    var nextReadFailure: LocalPolicyFailure? = null
    var cancelNextReplace: Boolean = false
    private var nextReadGate: CompletableDeferred<Unit>? = null

    fun replaceExternally(
        domains: List<String> = state.policy.domains.map { it.canonicalValue },
        applicationPolicyName: String? = null,
    ) {
        state = stateOf(state.revision + 1, domains, applicationPolicyName)
    }

    fun suspendNextRead(): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().also { nextReadGate = it }
    }

    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        readCalls++
        nextReadGate?.let { gate ->
            nextReadGate = null
            gate.await()
        }
        val failure = nextReadFailure
        if (failure != null) {
            nextReadFailure = null
            return LocalPolicyResult.Failure(failure)
        }
        return LocalPolicyResult.Success(state)
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        replaceCalls++
        if (cancelNextReplace) {
            cancelNextReplace = false
            throw CancellationException("synthetic cancellation")
        }
        if (expectedRevision != state.revision) {
            return LocalPolicyResult.Failure(LocalPolicyFailure.REVISION_CONFLICT)
        }
        state = LocalTargetPolicyState(expectedRevision + 1, policy)
        return LocalPolicyResult.Success(state)
    }
}

private fun stateOf(
    revision: Long,
    domains: List<String> = emptyList(),
    applicationPolicyName: String? = null,
): LocalTargetPolicyState {
    val result = TargetPolicy.fromStoredValues(domains, applicationPolicyName)
    val policy = (result as TargetPolicyValidationResult.Success).policy

    return LocalTargetPolicyState(revision, policy)
}
