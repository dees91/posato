package app.posato.feature.update

import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.update.data.MaintenanceCloseOutcome
import app.posato.feature.update.data.MaintenanceGate
import app.posato.feature.update.data.MaintenanceStoreFailure
import app.posato.feature.update.data.MaintenanceStoreResult
import app.posato.feature.update.data.UpdateMaintenanceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaintenanceAdmissionTest {
    @Test
    fun `given an open gate when enforcement applies then the request reaches the helper`() = runTest {
        val port = RecordingPort()
        val gated = GatedEnforcementPort(port, MaintenanceAdmission(InMemoryMaintenanceStore(), clock = { NOW }))

        val report = gated.apply(REQUEST)

        assertEquals(EnforcementOutcome.APPLIED, report.outcome)
        assertEquals(1, port.applied)
    }

    @Test
    fun `given a closed gate when enforcement applies then it is refused without reaching the helper`() = runTest {
        val port = RecordingPort()
        val admission = MaintenanceAdmission(InMemoryMaintenanceStore(), clock = { NOW })
        assertEquals(MaintenanceCloseResult.Closed, admission.close(FROM_BUILD, TARGET_BUILD))

        val report = GatedEnforcementPort(port, admission).apply(REQUEST)

        assertEquals(EnforcementOutcome.FAILED, report.outcome)
        assertEquals(0, port.applied)
    }

    @Test
    fun `given an unreadable gate when enforcement applies then it is refused`() = runTest {
        val port = RecordingPort()
        val store = InMemoryMaintenanceStore(readFailure = MaintenanceStoreFailure.STORAGE_FAILURE)

        val report = GatedEnforcementPort(port, MaintenanceAdmission(store, clock = { NOW })).apply(REQUEST)

        assertEquals(EnforcementOutcome.FAILED, report.outcome)
        assertEquals(0, port.applied)
    }

    @Test
    fun `given an apply in flight when the gate closes then closing waits for the apply to finish`() = runTest {
        val release = CompletableDeferred<Unit>()
        val port = RecordingPort(beforeApply = { release.await() })
        val admission = MaintenanceAdmission(InMemoryMaintenanceStore(), clock = { NOW })
        val gated = GatedEnforcementPort(port, admission)

        val applying = async { gated.apply(REQUEST) }
        runCurrent()
        val closing = async { admission.close(FROM_BUILD, TARGET_BUILD) }
        runCurrent()

        assertFalse(closing.isCompleted)
        release.complete(Unit)
        assertEquals(EnforcementOutcome.APPLIED, applying.await().outcome)
        assertEquals(MaintenanceCloseResult.Closed, closing.await())
    }

    @Test
    fun `given an active session when the gate closes then admission is refused and the gate stays open`() = runTest {
        val store = InMemoryMaintenanceStore(closeOutcome = MaintenanceCloseOutcome.SESSION_ACTIVE)
        val admission = MaintenanceAdmission(store, clock = { NOW })

        assertEquals(MaintenanceCloseResult.SessionActive, admission.close(FROM_BUILD, TARGET_BUILD))
        assertEquals(MaintenanceGate.Open, store.gate)
    }

    @Test
    fun `given an admitted cycle when reopening is evaluated then the gate stays closed without consulting evidence`() = runTest {
        val store = InMemoryMaintenanceStore()
        val admission = MaintenanceAdmission(store, clock = { NOW })
        admission.close(FROM_BUILD, TARGET_BUILD)
        admission.markCycleAdmitted()
        var consulted = false

        val result = admission.reopenWhen { consulted = true; true }

        assertEquals(MaintenanceReopenResult.CycleInProgress, result)
        assertFalse(consulted)
        assertTrue(store.gate is MaintenanceGate.Closed)
    }

    @Test
    fun `given an ended cycle and positive evidence when reopening is evaluated then the gate opens`() = runTest {
        val store = InMemoryMaintenanceStore()
        val admission = MaintenanceAdmission(store, clock = { NOW })
        admission.close(FROM_BUILD, TARGET_BUILD)
        admission.markCycleAdmitted()
        admission.markCycleEnded()

        val result = admission.reopenWhen { gate -> gate == ClosedMaintenanceGate(FROM_BUILD, TARGET_BUILD, NOW) }

        assertEquals(MaintenanceReopenResult.Reopened, result)
        assertEquals(MaintenanceGate.Open, store.gate)
    }

    @Test
    fun `given missing evidence when reopening is evaluated then the gate stays closed`() = runTest {
        val store = InMemoryMaintenanceStore()
        val admission = MaintenanceAdmission(store, clock = { NOW })
        admission.close(FROM_BUILD, TARGET_BUILD)

        assertEquals(MaintenanceReopenResult.EvidenceMissing, admission.reopenWhen { false })
        assertTrue(store.gate is MaintenanceGate.Closed)
    }

    @Test
    fun `given an open gate when reopening is evaluated then evidence is not consulted`() = runTest {
        val admission = MaintenanceAdmission(InMemoryMaintenanceStore(), clock = { NOW })
        var consulted = false

        assertEquals(MaintenanceReopenResult.AlreadyOpen, admission.reopenWhen { consulted = true; true })
        assertFalse(consulted)
    }

    private class RecordingPort(
        private val beforeApply: suspend () -> Unit = {},
    ) : EnforcementPort {
        var applied: Int = 0

        override val reapplyRequiresPrompt: Boolean = true

        override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
            beforeApply()
            applied += 1
            return EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
        }

        override suspend fun clear(): EnforcementOutcome {
            return EnforcementOutcome.CLEARED
        }

        override suspend fun status(): EnforcementOutcome {
            return EnforcementOutcome.CLEARED
        }
    }

    private companion object {
        const val NOW: Long = 1_700_000_000_000L
        const val FROM_BUILD: String = "8"
        const val TARGET_BUILD: String = "9"
        val REQUEST: EnforcementRequest = EnforcementRequest(listOf("example.com"), emptyList(), "session", NOW, NOW + 60_000L)
    }
}

internal class InMemoryMaintenanceStore(
    private val readFailure: MaintenanceStoreFailure? = null,
    private val closeOutcome: MaintenanceCloseOutcome = MaintenanceCloseOutcome.CLOSED,
) : UpdateMaintenanceStore {
    var gate: MaintenanceGate = MaintenanceGate.Open

    override suspend fun read(): MaintenanceStoreResult<MaintenanceGate> {
        return readFailure?.let { MaintenanceStoreResult.Failure(it) } ?: MaintenanceStoreResult.Success(gate)
    }

    override suspend fun close(
        fromBuild: String,
        targetBuild: String,
        nowEpochMillis: Long,
    ): MaintenanceStoreResult<MaintenanceCloseOutcome> {
        if (closeOutcome == MaintenanceCloseOutcome.CLOSED) {
            gate = MaintenanceGate.Closed(fromBuild, targetBuild, nowEpochMillis)
        }
        return MaintenanceStoreResult.Success(closeOutcome)
    }

    override suspend fun reopen(): MaintenanceStoreResult<Unit> {
        gate = MaintenanceGate.Open
        return MaintenanceStoreResult.Success(Unit)
    }
}
