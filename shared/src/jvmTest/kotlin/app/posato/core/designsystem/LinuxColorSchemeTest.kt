package app.posato.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Posato reads the desktop's color scheme from `gsettings get` output, which the E2E run only shows for the two
 * values a person picks in Settings. A missing key, an older GNOME without it, or a failing command must keep the
 * light palette instead of failing or turning dark.
 */
class LinuxColorSchemeTest {
    @Test
    fun `given gsettings output when it is read then only prefer-dark selects the dark palette`() {
        val outputs = mapOf(
            "'prefer-dark'\n" to true,
            "'prefer-light'\n" to false,
            "'default'\n" to false,
            "" to false,
            "No such key “color-scheme”\n" to false,
        )

        assertEquals(outputs, outputs.mapValues { (output, _) -> prefersDark(output) })
    }
}
