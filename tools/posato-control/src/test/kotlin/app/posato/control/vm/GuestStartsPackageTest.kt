package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every relayed command that may start Posato must pass the package guard, or a rebuild without `vm sync` is verified
 * against the stale guest package. Element commands start Posato when it is not running, so only the paths that never
 * launch it may skip the guard.
 */
class GuestStartsPackageTest {
    @Test
    fun `element commands start Posato when it is not running`() {
        listOf("tap", "type", "press", "wait", "update-consent").forEach { command ->
            assertTrue(startsPackage(listOf(command, "-t", "desktop", "--text", "Start"), scenario = null), command)
        }
        assertTrue(startsPackage(listOf("tap", "-t", "desktop", "--process", "PosatoMacOSHelper", "--id", "x"), scenario = null))
    }

    @Test
    fun `typing into or waiting on another process without a query leaves Posato alone`() {
        assertFalse(startsPackage(listOf("type", "-t", "desktop", "--process", "Safari", "--input", "a"), scenario = null))
        assertFalse(startsPackage(listOf("wait", "-t", "desktop", "--process=Safari", "--for", "exists"), scenario = null))
        assertFalse(startsPackage(listOf("snapshot", "-t", "desktop"), scenario = null))
    }

    @Test
    fun `a scenario starts Posato unless its own launch is skipped, from a file or standard input alike`() {
        val run = listOf("run", "-t", "desktop", "--scenario", "-")
        assertFalse(startsPackage(run, scenario = """{"launch": {"skip": true}, "steps": []}"""))
        assertTrue(startsPackage(run, scenario = """{"launch": {"skip": false}, "steps": [{"action": "tap", "query": {"skip": true}}]}"""))
        assertTrue(startsPackage(run, scenario = """{"steps": []}"""))
        assertTrue(startsPackage(run, scenario = "not json"))
    }
}
