package app.posato.feature.targets.ui

import app.posato.feature.targets.data.LocalExactDomainPolicyState
import app.posato.feature.targets.data.LocalExactDomainPolicyStore
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.domain.ExactDomainPolicy
import app.posato.feature.targets.domain.ExactDomainPolicyValidationResult
import kotlinx.coroutines.CancellationException
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

class ExactDomainsViewModelTest {
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
        val store = FakeExactDomainPolicyStore(stateOf(0))
        ExactDomainsViewModel(store)
        scheduler.runCurrent()

        assertEquals(0, store.readCalls)
    }

    @Test
    fun `given uiState when collection starts then storage is read`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(0))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals(1, store.readCalls)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `given uiState collection stops when timeout passes and collection restarts then storage is read again`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(0))
        val viewModel = ExactDomainsViewModel(store)
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
        val store = FakeExactDomainPolicyStore(stateOf(7, "stable.example"))
        store.nextReadFailure = LocalPolicyFailure.CORRUPTION
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals(ExactDomainsOperationFailure.CORRUPTED_POLICY, viewModel.uiState.value.operationFailure)
        assertFalse(viewModel.uiState.value.hasLoaded)

        viewModel.retry()
        scheduler.runCurrent()

        assertEquals(null, viewModel.uiState.value.operationFailure)
        assertEquals(listOf("stable.example"), viewModel.uiState.value.domains)
    }

    @Test
    fun `given an empty policy when adding editing and removing then persisted snapshots drive the state`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(0))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)

        scheduler.runCurrent()
        viewModel.submit("EXAMPLE.COM")
        scheduler.runCurrent()

        assertEquals(listOf("example.com"), viewModel.uiState.value.domains)
        assertEquals(1L, viewModel.uiState.value.editorSession)

        viewModel.beginEditing("example.com")
        scheduler.runCurrent()
        assertEquals(2L, viewModel.uiState.value.editorSession)
        viewModel.submit("updated.example")
        scheduler.runCurrent()

        assertEquals(listOf("updated.example"), viewModel.uiState.value.domains)
        assertEquals(3L, viewModel.uiState.value.editorSession)

        viewModel.remove("updated.example")
        scheduler.runCurrent()

        assertEquals(emptyList(), viewModel.uiState.value.domains)
        assertEquals(4L, viewModel.uiState.value.editorSession)
        assertEquals(3, store.replaceCalls)
    }

    @Test
    fun `given a canonical duplicate when submitted then it is rejected without replacing storage`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(4, "example.com"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.submit("EXAMPLE.COM.")
        scheduler.runCurrent()

        assertEquals(ExactDomainEntryFailure.DUPLICATE, viewModel.uiState.value.inputFailure)
        assertEquals(listOf("example.com"), viewModel.uiState.value.domains)
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun `given an invalid edit when submitted then the previous valid row remains`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(2, "stable.example"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.beginEditing("stable.example")
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.editorSession
        viewModel.submit("https://invalid.example")
        scheduler.runCurrent()

        assertEquals(ExactDomainEntryFailure.INVALID_DOMAIN, viewModel.uiState.value.inputFailure)
        assertEquals(editorSession, viewModel.uiState.value.editorSession)
        assertEquals("stable.example", viewModel.uiState.value.editingDomain)
        assertEquals(listOf("stable.example"), viewModel.uiState.value.domains)
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun `given a revision conflict when saving then valid state and edit input remain visible`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(3, "stable.example"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        store.nextReplaceFailure = LocalPolicyFailure.REVISION_CONFLICT

        viewModel.beginEditing("stable.example")
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.editorSession
        viewModel.submit("replacement.example")
        scheduler.runCurrent()

        assertEquals(ExactDomainsOperationFailure.REVISION_CONFLICT, viewModel.uiState.value.operationFailure)
        assertEquals(listOf("stable.example"), viewModel.uiState.value.domains)
        assertEquals(editorSession, viewModel.uiState.value.editorSession)
        assertEquals("stable.example", viewModel.uiState.value.editingDomain)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `given an edited domain was removed externally when conflict reloads then the editor is reset`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(3, "stable.example"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditing("stable.example")
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.editorSession
        store.replaceExternally("other.example")

        viewModel.submit("replacement.example")
        scheduler.runCurrent()
        assertEquals(ExactDomainsOperationFailure.REVISION_CONFLICT, viewModel.uiState.value.operationFailure)

        viewModel.retry()
        scheduler.runCurrent()

        assertEquals(listOf("other.example"), viewModel.uiState.value.domains)
        assertEquals(null, viewModel.uiState.value.editingDomain)
        assertEquals(editorSession + 1, viewModel.uiState.value.editorSession)
        assertEquals(null, viewModel.uiState.value.operationFailure)

        viewModel.submit("replacement.example")
        scheduler.runCurrent()

        assertEquals(listOf("other.example", "replacement.example"), viewModel.uiState.value.domains)
        assertEquals(2, store.replaceCalls)
    }

    @Test
    fun `given an edited domain remains externally when conflict reloads then the editor is preserved`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(3, "stable.example"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        viewModel.beginEditing("stable.example")
        scheduler.runCurrent()
        val editorSession = viewModel.uiState.value.editorSession
        store.replaceExternally("stable.example", "other.example")

        viewModel.submit("replacement.example")
        scheduler.runCurrent()
        viewModel.retry()
        scheduler.runCurrent()

        assertEquals(listOf("other.example", "stable.example"), viewModel.uiState.value.domains)
        assertEquals("stable.example", viewModel.uiState.value.editingDomain)
        assertEquals(editorSession, viewModel.uiState.value.editorSession)
        assertEquals(null, viewModel.uiState.value.operationFailure)

        viewModel.submit("replacement.example")
        scheduler.runCurrent()

        assertEquals(listOf("other.example", "replacement.example"), viewModel.uiState.value.domains)
        assertEquals(2, store.replaceCalls)
    }

    @Test
    fun `given cancellation when saving then valid state is not replaced or left busy`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(1, "stable.example"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        store.cancelNextReplace = true

        viewModel.submit("new.example")
        scheduler.runCurrent()

        assertEquals(listOf("stable.example"), viewModel.uiState.value.domains)
        assertEquals(0L, viewModel.uiState.value.editorSession)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `given an active edit when cancelled then a new empty editor session is exposed`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(1, "stable.example"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.beginEditing("stable.example")
        scheduler.runCurrent()
        val editingSession = viewModel.uiState.value.editorSession

        viewModel.cancelEditing()
        scheduler.runCurrent()

        assertEquals(editingSession + 1, viewModel.uiState.value.editorSession)
        assertEquals(null, viewModel.uiState.value.editingDomain)
    }

    @Test
    fun `given state with domains when rendered as text then input and domains remain redacted`() = runTest(dispatcher) {
        val store = FakeExactDomainPolicyStore(stateOf(1, "private.example"))
        val viewModel = ExactDomainsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals("ExactDomainsUiState(redacted)", viewModel.uiState.value.toString())
    }
}

private fun TestScope.observe(viewModel: ExactDomainsViewModel): Job {
    return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect()
    }
}

private class FakeExactDomainPolicyStore(
    private var state: LocalExactDomainPolicyState,
) : LocalExactDomainPolicyStore {
    var readCalls: Int = 0
    var replaceCalls: Int = 0
    var nextReadFailure: LocalPolicyFailure? = null
    var nextReplaceFailure: LocalPolicyFailure? = null
    var cancelNextReplace: Boolean = false

    fun replaceExternally(vararg domains: String) {
        state = stateOf(state.revision + 1, *domains)
    }

    override suspend fun read(): LocalPolicyResult<LocalExactDomainPolicyState> {
        readCalls++
        val failure = nextReadFailure
        if (failure != null) {
            nextReadFailure = null
            return LocalPolicyResult.Failure(failure)
        }
        return LocalPolicyResult.Success(state)
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: ExactDomainPolicy,
    ): LocalPolicyResult<LocalExactDomainPolicyState> {
        replaceCalls++
        val failure = nextReplaceFailure

        return when {
            cancelNextReplace -> {
                cancelNextReplace = false
                throw CancellationException("synthetic cancellation")
            }

            failure != null -> {
                nextReplaceFailure = null
                LocalPolicyResult.Failure(failure)
            }

            expectedRevision != state.revision -> {
                LocalPolicyResult.Failure(LocalPolicyFailure.REVISION_CONFLICT)
            }

            else -> {
                state = LocalExactDomainPolicyState(expectedRevision + 1, policy)
                LocalPolicyResult.Success(state)
            }
        }
    }
}

private fun stateOf(
    revision: Long,
    vararg domains: String,
): LocalExactDomainPolicyState {
    val policy = when (val result = ExactDomainPolicy.fromCanonicalValues(domains.asList())) {
        is ExactDomainPolicyValidationResult.Success -> result.policy
        is ExactDomainPolicyValidationResult.Failure -> error("invalid test policy")
    }

    return LocalExactDomainPolicyState(revision, policy)
}
