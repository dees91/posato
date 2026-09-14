package app.posato.feature.licenses

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LicenseMarkdownTest {
    @Test
    fun `headings and soft lines render without source markers`() {
        val blocks = parseLicenseMarkdown("## Runtime components\n\nFirst line\ncontinued here.")
        val heading = blocks[0] as LicenseMarkdownBlock.Text
        val paragraph = blocks[1] as LicenseMarkdownBlock.Text

        assertTrue(heading.heading)
        assertEquals("Runtime components", heading.content.text)
        assertEquals("First line continued here.", paragraph.content.text)
    }

    @Test
    fun `component table preserves every cell and associates its headers`() {
        val blocks = parseLicenseMarkdown(
            """
            | Component | Used by | License |
            | --- | --- | --- |
            | Example **runtime** | macOS, iOS | Apache-2.0 |
            | Other runtime | macOS | MIT |
            """.trimIndent(),
        ).map { it as LicenseMarkdownBlock.TableRow }

        assertEquals(2, blocks.size)
        assertEquals(listOf("Component", "Used by", "License"), blocks[0].headers.map { it.text })
        assertEquals(listOf("Example runtime", "macOS, iOS", "Apache-2.0"), blocks[0].cells.map { it.text })
        assertEquals(listOf("Other runtime", "macOS", "MIT"), blocks[1].cells.map { it.text })
        assertEquals(listOf(true, false), blocks.map { it.first })
    }

    @Test
    fun `attributions preserve copyright with bold labels and inline code`() {
        val block = parseLicenseMarkdown("- **Example:** Copyright 2026. See `legal` notices.").single() as LicenseMarkdownBlock.Text

        assertEquals("• Example: Copyright 2026. See legal notices.", block.content.text)
        assertTrue(block.content.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
        assertTrue(block.content.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun `license link retains the readable label and local destination`() {
        val block = parseLicenseMarkdown("Under [Apache License, Version 2.0](LICENSE).").single() as LicenseMarkdownBlock.Text
        val link = block.content.getStringAnnotations("document-link", 0, block.content.length).single()

        assertEquals("Under Apache License, Version 2.0.", block.content.text)
        assertEquals("LICENSE", link.item)
        assertEquals("Apache License, Version 2.0", block.content.subSequence(link.start, link.end).text)
    }

    @Test
    fun `unknown blocks remain readable text instead of being discarded`() {
        val block = parseLicenseMarkdown("<custom>Keep this notice.</custom>").single() as LicenseMarkdownBlock.Text

        assertEquals("<custom>Keep this notice.</custom>", block.content.text)
    }

    @Test
    fun `literal code retains line breaks and does not apply inline formatting`() {
        val block = parseLicenseMarkdown("```text\nCopyright 2026\n**Literal** notice\n```").single() as LicenseMarkdownBlock.Text

        assertEquals("Copyright 2026\n**Literal** notice", block.content.text)
    }
}
