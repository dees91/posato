package app.posato.feature.transfer

import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.SyncScheduleStore
import app.posato.feature.schedules.domain.LocalMinute
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.ui.FakeSessionMappings
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.bootstrap.AppleSyncTestHarness
import app.posato.feature.sync.bootstrap.scheduleLink
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.choiceSet
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.transfer.data.ImportWriter
import app.posato.feature.transfer.data.PauseSetTransfer
import app.posato.feature.transfer.data.TransferImport
import app.posato.feature.transfer.data.TransferRead
import app.posato.feature.transfer.domain.ImportMode
import app.posato.feature.transfer.domain.ImportPlanResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole path a person takes: export on one device, open the file on another, preview, import, and turn
 * the imported schedules on. Two separate databases stand in for two devices that share nothing.
 */
class PauseSetTransferTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(10)))
    private val evening = checkNotNull(PauseSetId.of(testIdentifier(11)))

    @Test
    fun `given a coded export when the other device opens it with the code and combines it then it holds the sets and schedules switched off`() = runTest {
        val source = harness("transfer-source")
        val target = harness("transfer-target")
        try {
            source.replaceLocal(checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", domains("example.com")), LocalPauseSet(work, "Work", domains("example.net"))), work)))
            assertIs<ScheduleResult.Success<*>>(source.schedules.save(SchedulePlan(ScheduleId("00000000000040008000000000000001"), "Deep work", 0b11111, 540, 660, enabled = true, setId = work), null))

            val exported = assertNotNull(source.transfer().let { transfer -> transfer.export(transfer.newCode()) })
            val code = assertNotNull(exported.code)
            assertIs<TransferRead.CodeNeeded>(target.transfer().read(exported.bytes, null))
            val file = assertIs<TransferRead.File>(target.transfer().read(exported.bytes, code)).file
            val preview = checkNotNull(target.transfer().preview(file, ImportMode.COMBINE))

            val imported = assertIs<TransferImport.Imported>(target.transfer().import(preview))

            // Combine keeps this device's own first-set name, here the unnamed one of a fresh install.
            assertEquals(setOf(null, "Work"), target.sets().sets.map { set -> set.name }.toSet())
            assertEquals(domains("example.com"), target.sets().domainsOf(PauseSetId.FIRST))
            assertEquals(listOf(false), target.plans().map(SchedulePlan::enabled))
            assertTrue(target.transfer().turnOn(imported.schedules))
            assertEquals(listOf(true), target.plans().map(SchedulePlan::enabled))
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun `given a replace that removes a set then this device's apps stay only for the sets that remain`() = runTest {
        val target = harness("transfer-replace")
        val mappings = FakeSessionMappings()
        try {
            target.replaceLocal(checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList()), LocalPauseSet(evening, "Evening", emptyList())), null)))
            val exported = assertNotNull(target.transfer(mappings).export(code = null))
            assertNull(exported.code)
            target.replaceLocal(checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList()), LocalPauseSet(work, "Work", emptyList())), null)))
            val file = assertIs<TransferRead.File>(target.transfer(mappings).read(exported.bytes, null)).file
            val preview = checkNotNull(target.transfer(mappings).preview(file, ImportMode.REPLACE))
            assertIs<ImportPlanResult.Planned>(preview.result)

            assertIs<TransferImport.Imported>(target.transfer(mappings).import(preview))

            assertEquals(listOf(target.sets().sets.map { set -> set.id.choiceSet() }.toSet()), mappings.retained)
            assertTrue(work.choiceSet() !in mappings.retained.single())
        } finally {
            target.close()
        }
    }

    @Test
    fun `given an imported schedule whose window is open now then turning it on is flagged first`() = runTest {
        val target = harness("transfer-window")
        try {
            val file = assertIs<TransferRead.File>(
                target.transfer().read(
                    """{"format":"posato-pause-sets","version":1,"sets":[{"ref":"a","name":null,"first":true,"domains":[]}],"schedules":[{"name":"Now","weekdays":127,"start":600,"end":720,"set":"a"}]}""".encodeToByteArray(),
                    null,
                ),
            ).file
            val imported = assertIs<TransferImport.Imported>(target.transfer().import(checkNotNull(target.transfer().preview(file, ImportMode.COMBINE))))

            assertEquals(listOf("Now"), target.transfer().openNow(imported.schedules))
        } finally {
            target.close()
        }
    }

    private fun TestScope.harness(name: String): AppleSyncTestHarness {
        return AppleSyncTestHarness(StandardTestDispatcher(testScheduler), "$name.db", withSchedules = true)
    }

    private fun AppleSyncTestHarness.transfer(mappings: FakeSessionMappings = FakeSessionMappings()): PauseSetTransfer {
        val scheduleStore = SyncScheduleStore(schedules, sync.scheduleLink())
        return PauseSetTransfer(
            policy = syncPolicy,
            schedules = scheduleStore,
            writer = ImportWriter(database, dispatcher, syncPolicy, scheduleStore, sync.scheduleLink()) { emptySet<PauseSetId>() to emptySet() },
            crypto = codeCrypto(),
            applicationMappings = mappings,
            zone = UtcZone,
            now = { NOON_MONDAY },
            computation = dispatcher,
        )
    }

    private suspend fun AppleSyncTestHarness.sets(): PauseSets {
        return assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.read()).value.sets
    }

    private suspend fun AppleSyncTestHarness.plans(): List<SchedulePlan> {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(schedules.read()).value.schedules.map { stored -> stored.plan }
    }

    private suspend fun AppleSyncTestHarness.replaceLocal(sets: PauseSets) {
        val state = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.read()).value
        assertIs<LocalPolicyResult.Success<*>>(sqlPolicy.replaceSets(state.revision, sets))
    }
}

/** 2026-10-19 11:00 UTC, a Monday, inside a 10:00 to 12:00 window. */
private const val NOON_MONDAY: Long = 1_792_407_600_000L

private object UtcZone : ScheduleZone {
    override fun localAt(epochMillis: Long): LocalMinute {
        val days = epochMillis.floorDiv(DAY_MILLIS)
        val minute = (epochMillis.mod(DAY_MILLIS) / MINUTE_MILLIS).toInt()
        return LocalMinute(ScheduleDate.ofEpochDay(days), minute)
    }

    override fun instantOf(
        date: ScheduleDate,
        minuteOfDay: Int,
    ): Long {
        return date.epochDay * DAY_MILLIS + minuteOfDay * MINUTE_MILLIS
    }
}

private const val DAY_MILLIS: Long = 86_400_000L
private const val MINUTE_MILLIS: Long = 60_000L

/** The fake's crypto with a key derivation that keeps the code, as in the seal tests. */
private fun codeCrypto(): app.posato.feature.sync.data.SyncCryptoProvider {
    val fake = FakeSyncCryptoProvider()
    return object : app.posato.feature.sync.data.SyncCryptoProvider by fake {
        override fun pbkdf2HmacSha256(
            password: ByteArray,
            salt: ByteArray,
            iterations: Int,
            length: Int,
        ): ByteArray {
            return ByteArray(length) { index -> (password[index % password.size] * 31 + salt[index % salt.size] + index).toByte() }
        }
    }
}
