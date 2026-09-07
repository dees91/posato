package app.posato.prototype.model

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrototypeItemsTest {
    private fun items(): PrototypeState {
        return reducePrototype(PrototypeFixtures.ready(PrototypePlatform.Mac), ItemAction.OpenItems)
    }

    @Test
    fun `given active session with missing mapping when repaired then session end persists`() {
        val missing = reducePrototype(PrototypeFixtures.active(PrototypePlatform.Mac), RecoveryAction.RemoveMapping)
        val overview = reducePrototype(missing, SessionAction.ReturnToSession)
        val picker = reducePrototype(overview, ItemAction.OpenApplications)
        assertEquals(PrototypeSurface.AppPicker, picker.surface)
        val repaired = reducePrototype(picker, ItemAction.SaveApplications(persistentListOf(PrototypeFixtures.APPLICATION)))
        assertEquals(PrototypeSurface.Active, repaired.surface)
        assertTrue(repaired.session.active)
        assertEquals("18:30", repaired.session.endsAt)
        assertEquals(1, repaired.localApplications().size)
    }

    @Test
    fun `given active session with valid mapping when opening general picker then action is blocked`() {
        val after = reducePrototype(PrototypeFixtures.active(PrototypePlatform.Mac), ItemAction.OpenApplications)
        assertEquals(PrototypeSurface.Active, after.surface)
        assertEquals(OutcomeTone.Blocked, after.outcome.tone)
    }

    @Test
    fun `given domain or URL when saved then only canonical exact host remains`() {
        val cases = mapOf(
            " HTTPS://NEWS.Example./article?q=private#anchor " to "news.example",
            "reading.example/path" to "reading.example",
            "https://bücher.example" to "xn--bcher-kva.example",
        )
        for ((input, domain) in cases) {
            assertEquals(PrototypeDomainResult.Valid(domain), parsePrototypeDomain(input))
        }
    }

    @Test
    fun `given invalid domains when edited then valid policy and editor remain`() {
        val opened = reducePrototype(items(), ItemAction.OpenDomain(PrototypeFixtures.DOMAIN))
        for (input in listOf(
            "",
            "localhost",
            "127.0.0.1",
            "https://user:secret@news.example",
            "ftp://news.example",
            "https://[::1]",
            "two..example",
            "bad_name.example",
            "-bad.example",
            "bad-.example",
            "a".repeat(64) + ".example",
        )) {
            val failed = reducePrototype(opened, ItemAction.SaveDomain(input))
            assertEquals(opened.policy, failed.policy, input)
            assertEquals(PrototypeSurface.DomainEditor, failed.surface)
            assertNotNull(failed.editor.domainError, input)
        }
    }

    @Test
    fun `given duplicate edit when rejected and cancelled then previous configuration persists`() {
        val added = reducePrototype(reducePrototype(items(), ItemAction.OpenDomain()), ItemAction.SaveDomain("reading.example"))
        val editor = reducePrototype(added, ItemAction.OpenDomain(PrototypeFixtures.DOMAIN))
        val failed = reducePrototype(editor, ItemAction.SaveDomain("https://reading.example/path"))
        assertEquals(added.policy, failed.policy)
        assertNotNull(failed.editor.domainError)
        val cancelled = reducePrototype(failed, ItemAction.Cancel)
        assertEquals(added.policy, cancelled.policy)
        assertEquals(PrototypeSurface.Items, cancelled.surface)
        val replaced = reducePrototype(editor, ItemAction.SaveDomain("changed.example"))
        assertEquals(persistentListOf("changed.example", "reading.example"), replaced.policy.domains)
        assertTrue(replaced.sync.pendingWork)
        val removed = reducePrototype(replaced, ItemAction.RemoveDomain("changed.example"))
        assertEquals(persistentListOf("reading.example"), removed.policy.domains)
    }

    @Test
    fun `given application draft when empty or cancelled then old mappings remain`() {
        val before = items()
        val picker = reducePrototype(before, ItemAction.OpenApplications)
        val invalid = reducePrototype(picker, ItemAction.SaveApplications(persistentListOf()))
        assertEquals(before.policy, invalid.policy)
        assertNotNull(invalid.editor.applicationError)
        assertEquals(before.policy, reducePrototype(invalid, ItemAction.Cancel).policy)
    }

    @Test
    fun `given Mac selection when saved or removed then iPhone mapping is independent`() {
        val before = items()
        val names = persistentListOf("Community Desk", "Loop Video", "Loop Video", "Not a fixture")
        val after = reducePrototype(reducePrototype(before, ItemAction.OpenApplications), ItemAction.SaveApplications(names))
        assertEquals(persistentListOf("Community Desk", "Loop Video"), after.localApplications())
        assertEquals(before.policy.mappings[PrototypePlatform.IPhone], after.policy.mappings[PrototypePlatform.IPhone])
        val removed = reducePrototype(after, ItemAction.RemoveApplication("Loop Video"))
        assertEquals(persistentListOf("Community Desk"), removed.localApplications())
        assertEquals(before.policy.mappings[PrototypePlatform.IPhone], removed.policy.mappings[PrototypePlatform.IPhone])
    }
}
