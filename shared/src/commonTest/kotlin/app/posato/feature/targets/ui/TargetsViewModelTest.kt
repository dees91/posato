package app.posato.feature.targets.ui

import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.data.LocalApplicationRemovalFailure
import app.posato.feature.targets.data.LocalApplicationRemovalResult
import app.posato.feature.targets.data.LocalApplicationSelectionRejection
import app.posato.feature.targets.data.LocalApplicationSelectionResult
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

class TargetsDesignAdoptionViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Test
    fun `given a stale add callback during editing then rejection releases the pending draft`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0, listOf("first.example")))
            val viewModel = TargetsViewModel(store)
            observe(viewModel)
            scheduler.runCurrent()
            viewModel.beginEditingDomain("first.example")
            scheduler.runCurrent()
            val browser = TargetsBrowserState()
            browser.websiteDraft.edit { append("next.example") }

            browser.submit(viewModel::submitWebsites)
            scheduler.runCurrent()
            browser.accept(viewModel.uiState.value.websiteBatchReceipt)

            assertEquals("next.example", browser.websiteDraft.text.toString())
            assertFalse(checkNotNull(browser.lastReceipt).saved)
            assertEquals(0, store.replaceCalls)
            viewModel.cancelEditingDomain()
            scheduler.runCurrent()
            browser.submit(viewModel::submitWebsites)
            scheduler.runCurrent()
            browser.accept(viewModel.uiState.value.websiteBatchReceipt)
            assertTrue(checkNotNull(browser.lastReceipt).saved)
        }
    }

    @Test
    fun `given a cancelled batch save when retried then the draft receives a failed receipt and can be resubmitted`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0))
            val viewModel = TargetsViewModel(store)
            observe(viewModel)
            scheduler.runCurrent()
            store.cancelNextReplace = true

            viewModel.submitWebsites("first.example", 1)
            scheduler.runCurrent()

            assertFalse(checkNotNull(viewModel.uiState.value.websiteBatchReceipt).saved)
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(emptyList(), viewModel.uiState.value.domains)
            viewModel.submitWebsites("first.example", 2)
            scheduler.runCurrent()
            assertTrue(checkNotNull(viewModel.uiState.value.websiteBatchReceipt).saved)
            assertEquals(listOf("first.example", "www.first.example"), viewModel.uiState.value.domains)
        }
    }

    @Test
    fun `given a custom group when native selection succeeds then its name is preserved`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0, applicationPolicyName = "Quiet work"))
            val mappings = FakeApplicationMappings()
            mappings.selectionResult = LocalApplicationSelectionResult.Success(snapshotOf(mapping("Example App", "ab")))
            val viewModel = TargetsViewModel(store, mappings)
            observe(viewModel)
            scheduler.runCurrent()

            viewModel.chooseApplications()
            scheduler.runCurrent()

            assertEquals("Quiet work", viewModel.uiState.value.applicationPolicyName)
            assertEquals(0, store.replaceCalls)
        }
    }

    @Test
    fun `given a batch when submitted then one revision saves all valid unique websites`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0))
            val viewModel = TargetsViewModel(store)
            observe(viewModel)
            scheduler.runCurrent()

            viewModel.submitWebsites("https://first.example/path, invalid, second.example, FIRST.EXAMPLE", 1)
            scheduler.runCurrent()

            assertEquals(
                listOf("first.example", "second.example", "www.first.example", "www.second.example"),
                viewModel.uiState.value.domains,
            )
            assertEquals(1, store.replaceCalls)
            val receipt = checkNotNull(viewModel.uiState.value.websiteBatchReceipt)
            assertEquals(1L, receipt.submissionId)
            assertTrue(receipt.saved)
            assertEquals(4, receipt.addedCount)
            assertEquals(1, receipt.duplicateCount)
            assertEquals(listOf(1), receipt.rejectedIndices)
        }
    }

    @Test
    fun `given a batch revision conflict when submitted then no successful receipt or partial save is produced`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0))
            val viewModel = TargetsViewModel(store)
            observe(viewModel)
            scheduler.runCurrent()
            store.replaceExternally(domains = listOf("external.example"))

            viewModel.submitWebsites("first.example,second.example", 2)
            scheduler.runCurrent()

            assertEquals(TargetsOperationFailure.REVISION_CONFLICT, viewModel.uiState.value.operationFailure)
            assertFalse(checkNotNull(viewModel.uiState.value.websiteBatchReceipt).saved)
            viewModel.retry()
            scheduler.runCurrent()
            assertEquals(listOf("external.example"), viewModel.uiState.value.domains)
        }
    }

    @Test
    fun `given no application group when a nonempty selection succeeds then the default group is created`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0))
            val mappings = FakeApplicationMappings()
            mappings.selectionResult = LocalApplicationSelectionResult.Success(snapshotOf(mapping("Example App", "ab")))
            val viewModel = TargetsViewModel(store, mappings)
            observe(viewModel)
            scheduler.runCurrent()

            assertTrue(viewModel.uiState.value.canChooseApplications())
            viewModel.chooseApplications()
            scheduler.runCurrent()

            assertEquals("Applications", viewModel.uiState.value.applicationPolicyName)
            assertEquals(1, viewModel.uiState.value.applicationMappings.size)
            assertEquals(1, store.replaceCalls)
        }
    }

    @Test
    fun `given no application group when selection is cancelled or empty then no group is created`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0))
            val mappings = FakeApplicationMappings()
            val viewModel = TargetsViewModel(store, mappings)
            observe(viewModel)
            scheduler.runCurrent()

            viewModel.chooseApplications()
            scheduler.runCurrent()
            mappings.selectionResult = LocalApplicationSelectionResult.Success(LocalApplicationMappingsSnapshot.empty())
            viewModel.chooseApplications()
            scheduler.runCurrent()

            assertEquals(null, viewModel.uiState.value.applicationPolicyName)
            assertEquals(0, store.replaceCalls)
        }
    }

    @Test
    fun `given a saved selection when group creation conflicts then mappings survive and activation requires an explicit retry`() {
        runTest(dispatcher) {
            val store = FakeTargetPolicyStore(stateOf(0))
            val mappings = FakeApplicationMappings()
            mappings.selectionResult = LocalApplicationSelectionResult.Success(snapshotOf(mapping("Example App", "ab")))
            val viewModel = TargetsViewModel(store, mappings)
            observe(viewModel)
            scheduler.runCurrent()
            store.replaceExternally(domains = listOf("external.example"))

            viewModel.chooseApplications()
            scheduler.runCurrent()
            assertEquals(null, viewModel.uiState.value.applicationPolicyName)
            assertEquals(1, viewModel.uiState.value.applicationMappings.size)
            assertEquals(TargetsOperationFailure.REVISION_CONFLICT, viewModel.uiState.value.operationFailure)

            viewModel.retry()
            scheduler.runCurrent()
            assertEquals(null, viewModel.uiState.value.applicationPolicyName)
            viewModel.activateApplicationPolicy()
            scheduler.runCurrent()
            assertEquals("Applications", viewModel.uiState.value.applicationPolicyName)
            assertEquals(listOf("external.example"), viewModel.uiState.value.domains)
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
}

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
    fun `given a policy signal when observed then the snapshot re-reads without loading`() = runTest(dispatcher) {
        val store = FakeTargetPolicyStore(stateOf(0, domains = listOf("old.example")))
        val viewModel = TargetsViewModel(store)
        observe(viewModel)
        scheduler.runCurrent()
        assertEquals(1, store.readCalls)

        store.replaceExternally(domains = listOf("old.example", "new.example"))
        store.changes.tryEmit(Unit)
        scheduler.runCurrent()

        assertEquals(2, store.readCalls)
        assertEquals(listOf("new.example", "old.example"), viewModel.uiState.value.domains)
        assertFalse(viewModel.uiState.value.isLoading)
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

    @Test
    fun `given available mappings when choosing and removing then device snapshot changes independently`() = runTest(dispatcher) {
        val mapping = mapping("Browser", "01")
        val mappings = FakeApplicationMappings()
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.canChooseApplications())
        mappings.selectionResult = LocalApplicationSelectionResult.Success(snapshotOf(mapping))
        viewModel.chooseApplications()
        scheduler.runCurrent()
        assertEquals(listOf(mapping), viewModel.uiState.value.applicationMappings)

        mappings.removalResult = LocalApplicationRemovalResult.Success(LocalApplicationMappingsSnapshot.empty())
        viewModel.removeApplicationMapping(mapping.id)
        scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.applicationMappings.isEmpty())
        assertEquals(1, mappings.removeCalls)
    }

    @Test
    fun `given available mappings when clearing then the device snapshot is replaced once`() = runTest(dispatcher) {
        val mapping = mapping("Browser", "03")
        val mappings = FakeApplicationMappings(snapshotOf(mapping))
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.clearApplicationMappings()
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.applicationMappings.isEmpty())
        assertEquals(1, mappings.clearCalls)
    }

    @Test
    fun `given rejected selection when choosing then prior snapshot remains visible`() = runTest(dispatcher) {
        val mapping = mapping("Browser", "02")
        val mappings = FakeApplicationMappings(snapshotOf(mapping)).apply {
            selectionResult = LocalApplicationSelectionResult.Rejected(LocalApplicationSelectionRejection.SELF)
        }
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.chooseApplications()
        scheduler.runCurrent()

        assertEquals(listOf(mapping), viewModel.uiState.value.applicationMappings)
        assertEquals(ApplicationMappingFailure.SELF_SELECTION, viewModel.uiState.value.applicationMappingFailure)
    }

    @Test
    fun `given system selection when choosing then prior snapshot remains visible`() = runTest(dispatcher) {
        val mapping = mapping("Browser", "02")
        val mappings = FakeApplicationMappings(snapshotOf(mapping)).apply {
            selectionResult = LocalApplicationSelectionResult.Rejected(LocalApplicationSelectionRejection.SYSTEM)
        }
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.chooseApplications()
        scheduler.runCurrent()

        assertEquals(listOf(mapping), viewModel.uiState.value.applicationMappings)
        assertEquals(ApplicationMappingFailure.SYSTEM_SELECTION, viewModel.uiState.value.applicationMappingFailure)
    }

    @Test
    fun `given access change when choosing then retained snapshot and restricted state remain visible`() = runTest(dispatcher) {
        val mapping = mapping("Browser", "04")
        val mappings = FakeApplicationMappings().apply {
            selectionResult = LocalApplicationSelectionResult.AccessChanged(
                snapshotOf(mapping),
                LocalApplicationMappingsAccess.RESTRICTED,
            )
        }
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.chooseApplications()
        scheduler.runCurrent()

        assertEquals(listOf(mapping), viewModel.uiState.value.applicationMappings)
        assertEquals(LocalApplicationMappingsAccess.RESTRICTED, viewModel.uiState.value.applicationMappingsAccess)
    }

    @Test
    fun `given corrupted mappings when retrying then clear is available and choose stays blocked`() = runTest(dispatcher) {
        val mappings = FakeApplicationMappings().apply {
            loadFailure = LocalApplicationMappingsLoadFailure.CORRUPTION
        }
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals(ApplicationMappingFailure.CORRUPTED_MAPPINGS, viewModel.uiState.value.applicationMappingFailure)
        assertTrue(viewModel.uiState.value.canClearApplicationMappings())
        assertFalse(viewModel.uiState.value.canChooseApplications())

        viewModel.retryApplicationMappings()
        scheduler.runCurrent()

        assertEquals(ApplicationMappingFailure.CORRUPTED_MAPPINGS, viewModel.uiState.value.applicationMappingFailure)
        assertEquals(0, mappings.clearCalls)
        assertFalse(viewModel.uiState.value.canChooseApplications())
        assertTrue(viewModel.uiState.value.canClearApplicationMappings())
    }

    @Test
    fun `given corrupted mappings when clearing then choosing becomes available`() = runTest(dispatcher) {
        val mappings = FakeApplicationMappings().apply {
            loadFailure = LocalApplicationMappingsLoadFailure.CORRUPTION
        }
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.clearApplicationMappings()
        scheduler.runCurrent()

        assertEquals(null, viewModel.uiState.value.applicationMappingFailure)
        assertTrue(viewModel.uiState.value.canChooseApplications())
        assertEquals(1, mappings.clearCalls)
    }

    @Test
    fun `given a storage load failure when reviewing actions then clear stays unavailable`() = runTest(dispatcher) {
        val mappings = FakeApplicationMappings().apply {
            loadFailure = LocalApplicationMappingsLoadFailure.STORAGE
        }
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        assertEquals(ApplicationMappingFailure.LOAD_FAILED, viewModel.uiState.value.applicationMappingFailure)
        assertFalse(viewModel.uiState.value.canClearApplicationMappings())
        assertFalse(viewModel.uiState.value.canChooseApplications())
        viewModel.clearApplicationMappings()
        scheduler.runCurrent()
        assertEquals(0, mappings.clearCalls)
    }

    @Test
    fun `given a failed corruption clear when clearing again then choosing becomes available`() = runTest(dispatcher) {
        val mappings = FakeApplicationMappings().apply {
            loadFailure = LocalApplicationMappingsLoadFailure.CORRUPTION
            clearResult = LocalApplicationRemovalResult.Failure(LocalApplicationRemovalFailure.STORAGE)
        }
        val viewModel = TargetsViewModel(FakeTargetPolicyStore(stateOf(1, applicationPolicyName = "Apps")), mappings)
        observe(viewModel)
        scheduler.runCurrent()

        viewModel.clearApplicationMappings()
        scheduler.runCurrent()

        assertEquals(ApplicationMappingFailure.CORRUPTED_CLEAR_FAILED, viewModel.uiState.value.applicationMappingFailure)
        assertTrue(viewModel.uiState.value.canClearApplicationMappings())
        assertFalse(viewModel.uiState.value.canChooseApplications())
        assertEquals(1, mappings.clearCalls)

        mappings.clearResult = null
        viewModel.clearApplicationMappings()
        scheduler.runCurrent()

        assertEquals(null, viewModel.uiState.value.applicationMappingFailure)
        assertTrue(viewModel.uiState.value.canChooseApplications())
        assertEquals(2, mappings.clearCalls)
    }
}

private fun TestScope.observe(viewModel: TargetsViewModel): Job {
    return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
}

private class FakeTargetPolicyStore(
    private var state: LocalTargetPolicyState,
) : LocalTargetPolicyStore {
    override suspend fun <T> withWriteGate(block: suspend () -> T): T {
        return block()
    }

    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val policyChanges: Flow<Unit>
        get() = changes
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
        syncWrite: PolicySyncWrite?,
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

private class FakeApplicationMappings(
    private var snapshot: LocalApplicationMappingsSnapshot = LocalApplicationMappingsSnapshot.empty(),
) : LocalApplicationMappings {
    var selectionResult: LocalApplicationSelectionResult = LocalApplicationSelectionResult.Cancelled
    var removalResult: LocalApplicationRemovalResult = LocalApplicationRemovalResult.Success(snapshot)
    var loadFailure: LocalApplicationMappingsLoadFailure? = null
    var clearResult: LocalApplicationRemovalResult? = null
    var removeCalls: Int = 0
    var clearCalls: Int = 0

    override suspend fun load(): LocalApplicationMappingsLoadResult {
        val failure = loadFailure
        if (failure != null) {
            return LocalApplicationMappingsLoadResult.Failure(failure)
        }
        return LocalApplicationMappingsLoadResult.Success(snapshot)
    }

    override suspend fun chooseApplications(): LocalApplicationSelectionResult {
        return selectionResult.also { result ->
            snapshot = when (result) {
                is LocalApplicationSelectionResult.Success -> result.snapshot
                is LocalApplicationSelectionResult.AccessChanged -> result.snapshot
                else -> snapshot
            }
        }
    }

    override suspend fun remove(mappingId: LocalApplicationMappingId): LocalApplicationRemovalResult {
        removeCalls++
        return removalResult.also { result ->
            if (result is LocalApplicationRemovalResult.Success) snapshot = result.snapshot
        }
    }

    override suspend fun clear(): LocalApplicationRemovalResult {
        clearCalls++
        val forced = clearResult
        if (forced != null) {
            return forced
        }
        loadFailure = null
        snapshot = LocalApplicationMappingsSnapshot.empty()
        return LocalApplicationRemovalResult.Success(snapshot)
    }
}

private fun mapping(
    name: String,
    byte: String
): LocalApplicationMapping {
    val id = checkNotNull(LocalApplicationMappingId.restore(byte.repeat(32)))
    return checkNotNull(LocalApplicationMapping.restore(id, name))
}

private fun snapshotOf(vararg mappings: LocalApplicationMapping): LocalApplicationMappingsSnapshot {
    return checkNotNull(LocalApplicationMappingsSnapshot.restore(mappings.asList()))
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
