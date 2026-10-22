package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A verification run removed the test Apple Account's long-history zone that `SYNC-022` needed (2026-10-21). E2E
 * cannot exercise the refusal without a protected zone at stake, which is the very thing it must not destroy, so the
 * decision is tested here: a listed account refuses without `--destroy-fixture`, also when the guest's accounts cannot
 * be read, and an unreadable list refuses rather than protecting nothing.
 */
class ProtectedFixtureTest {
    private val fixtures = listOf(ProtectedFixture("test-account", "long-history", "SYNC-022 reproduces on it"))

    @Test
    fun `given a guest signed in to a protected account when removing then it refuses unless the fixture may go`() {
        val refusal = assertNotNull(protectedFixtureRefusal(fixtures, destroyFixture = false) { listOf("test-account") })
        assertEquals(ErrorCode.REFUSED_WITHOUT_CONFIRMATION, refusal.code)
        assertNull(protectedFixtureRefusal(fixtures, destroyFixture = true) { listOf("test-account") })
        assertNotNull(protectedFixtureRefusal(fixtures, destroyFixture = false) { listOf("Test-Account") })
    }

    @Test
    fun `given another account or no list when removing then it runs, and unreadable accounts refuse`() {
        assertNull(protectedFixtureRefusal(fixtures, destroyFixture = false) { listOf("other-account") })
        assertNull(protectedFixtureRefusal(emptyList(), destroyFixture = false) { null })
        assertNotNull(protectedFixtureRefusal(fixtures, destroyFixture = false) { null })
    }

    /** An option placed before the action once skipped the guard, so `flow icloud --timeout-seconds 900 remove` removed a fixture. */
    @Test
    fun `given options before the action when relaying flow icloud remove then the guard still applies`() {
        assertTrue(removesICloudWorkspace(listOf("flow", "icloud", "remove", "-t", "desktop")))
        assertTrue(removesICloudWorkspace(listOf("flow", "icloud", "--timeout-seconds", "900", "remove", "-t", "desktop")))
        assertFalse(removesICloudWorkspace(listOf("flow", "icloud", "--timeout-seconds", "900", "link", "-t", "desktop")))
    }

    @Test
    fun `given a list file when loading then a missing file protects nothing and a broken one refuses`() {
        val directory = Files.createTempDirectory("protected-fixtures")
        val file = directory.resolve("protected-fixtures.json")
        assertEquals(emptyList(), loadProtectedFixtures(file))
        Files.writeString(file, """[{"account": "test-account", "fixture": "long-history"}]""")
        assertEquals(listOf(ProtectedFixture("test-account", "long-history")), loadProtectedFixtures(file))
        Files.writeString(file, """{"account": "test-account"""")
        assertEquals(ErrorCode.REFUSED_WITHOUT_CONFIRMATION, assertFailsWith<ControlException> { loadProtectedFixtures(file) }.code)
    }
}
