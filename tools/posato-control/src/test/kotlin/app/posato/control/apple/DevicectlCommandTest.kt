package app.posato.control.apple

import kotlin.test.Test
import kotlin.test.assertEquals

class DevicectlCommandTest {
    /**
     * A language proof launches Posato with `-AppleLanguages (de)`. devicectl read such an argument as its own short
     * options, and the result file flag after it as the application's, so the launch failed and the app kept English.
     */
    @Test
    fun `application arguments that look like options reach the application after the result file`() {
        val command = devicectlCommand(
            listOf("device", "process", "launch", "--device", "PHONE"),
            "/tmp/result.json",
            listOf("app.posato.ios", "-AppleLanguages", "(de)"),
        )

        assertEquals(
            listOf(
                "devicectl", "device", "process", "launch", "--device", "PHONE", "--json-output", "/tmp/result.json",
                "--", "app.posato.ios", "-AppleLanguages", "(de)",
            ),
            command,
        )
    }

    @Test
    fun `a command without positional arguments ends with the result file`() {
        assertEquals(
            listOf("devicectl", "list", "devices", "--json-output", "/tmp/result.json"),
            devicectlCommand(listOf("list", "devices"), "/tmp/result.json", emptyList()),
        )
    }
}
