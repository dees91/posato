package app.posato.desktop.macos

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacFirefoxExtensionStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `given no firefox when detected then absent is reported`() = runTest(dispatcher) {
        withTempRoot { root ->
            val state = MacFirefoxExtensionState(dispatcher, applicationRoots = listOf(root))

            val detection = state.detect()

            assertFalse(detection.installed)
            assertEquals(null, detection.applicationName)
        }
    }

    @Test
    fun `given firefox in a later root when detected then it is named`() = runTest(dispatcher) {
        withTempRoot { empty ->
            withTempRoot { root ->
                Files.createDirectory(root.resolve("Firefox.app"))
                val state = MacFirefoxExtensionState(dispatcher, applicationRoots = listOf(empty, root))

                val detection = state.detect()

                assertTrue(detection.installed)
                assertEquals("Firefox", detection.applicationName)
            }
        }
    }

    @Test
    fun `given nightly only when detected then its channel is named`() = runTest(dispatcher) {
        withTempRoot { root ->
            Files.createDirectory(root.resolve("Firefox Nightly.app"))
            val state = MacFirefoxExtensionState(dispatcher, applicationRoots = listOf(root))

            val detection = state.detect()

            assertTrue(detection.installed)
            assertEquals("Firefox Nightly", detection.applicationName)
        }
    }

    @Test
    fun `given no firefox when installed then nothing launches`() = runTest(dispatcher) {
        withTempRoot { root ->
            var launched = false
            val state = MacFirefoxExtensionState(
                dispatcher,
                applicationRoots = listOf(root),
                extensionPackage = root.resolve("PosatoFirefoxExtension.xpi"),
                launch = { launched = true },
            )

            assertFalse(state.install())
            assertFalse(launched)
        }
    }

    @Test
    fun `given firefox and package when installed then the package opens in that firefox`() = runTest(dispatcher) {
        withTempRoot { root ->
            val application = Files.createDirectory(root.resolve("Firefox.app"))
            val packageFile = Files.createFile(root.resolve("PosatoFirefoxExtension.xpi"))
            val launched = mutableListOf<List<String>>()
            val state = MacFirefoxExtensionState(
                dispatcher,
                applicationRoots = listOf(root),
                extensionPackage = packageFile,
                launch = { command -> launched.add(command) },
            )

            assertTrue(state.install())
            assertEquals(
                listOf(listOf("/usr/bin/open", "-a", application.toString(), packageFile.toString())),
                launched,
            )
        }
    }

    @Test
    fun `given a failing launcher when installed then failure is reported`() = runTest(dispatcher) {
        withTempRoot { root ->
            Files.createDirectory(root.resolve("Firefox.app"))
            val packageFile = Files.createFile(root.resolve("PosatoFirefoxExtension.xpi"))
            val state = MacFirefoxExtensionState(
                dispatcher,
                applicationRoots = listOf(root),
                extensionPackage = packageFile,
                launch = { throw IllegalStateException("no opener") },
            )

            assertFalse(state.install())
        }
    }

    @Test
    fun `given extension status bodies when verified then only seen timestamps pass`() = runTest(dispatcher) {
        withTempRoot { root ->
            Files.createDirectory(root.resolve("Firefox.app"))

            suspend fun verified(body: String?): Boolean {
                val state = MacFirefoxExtensionState(
                    dispatcher,
                    applicationRoots = listOf(root),
                    fetchStatus = { body },
                )
                return state.verified()
            }

            assertTrue(verified("seen:1727600000000"))
            assertFalse(verified("unseen"))
            assertFalse(verified(null))
            assertFalse(verified("seen:"))
            assertFalse(verified("seen:soon"))
            assertFalse(verified("ok"))
        }
    }

    private suspend fun withTempRoot(block: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory("firefox-extension-test")
        try {
            block(root)
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
