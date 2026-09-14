package app.posato.feature.licenses

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

internal sealed interface LicenseMarkdownBlock {
    data class Text(
        val content: AnnotatedString,
        val heading: Boolean = false
    ) : LicenseMarkdownBlock

    data class TableRow(
        val headers: List<AnnotatedString>,
        val cells: List<AnnotatedString>,
        val first: Boolean
    ) : LicenseMarkdownBlock
}

internal fun parseLicenseMarkdown(source: String): List<LicenseMarkdownBlock> {
    val tree = MarkdownParser(
        GFMFlavourDescriptor(),
        cancellationToken = CancellationToken.NonCancellable,
    ).buildMarkdownTreeFromString(source as CharSequence)
    return tree.children.flatMap { it.licenseBlocks(source) }
}

private fun ASTNode.licenseBlocks(source: String): List<LicenseMarkdownBlock> = when (type) {
    MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> {
        emptyList()
    }

    MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
        children.flatMap { it.licenseBlocks(source) }
    }

    MarkdownElementTypes.LIST_ITEM -> {
        listOf(
            LicenseMarkdownBlock.Text(
                buildAnnotatedString {
                    val marker = children.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
                    append(marker?.rawText(source) ?: "•")
                    append(" ")
                    children.filter { it.type == MarkdownElementTypes.PARAGRAPH }.forEach { append(it.inlineText(source)) }
                },
            ),
        )
    }

    GFMElementTypes.TABLE -> {
        val headers = children.first { it.type == GFMElementTypes.HEADER }.tableCells(source)
        children.filter { it.type == GFMElementTypes.ROW }.mapIndexed { index, row ->
            LicenseMarkdownBlock.TableRow(headers, row.tableCells(source), first = index == 0)
        }
    }

    MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK -> {
        listOf(
            LicenseMarkdownBlock.Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(
                            children.filter {
                                it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || it.type == MarkdownTokenTypes.CODE_LINE
                            }.joinToString("\n") { it.rawText(source) },
                        )
                    }
                },
            ),
        )
    }

    else -> {
        val heading = children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT }
        listOf(LicenseMarkdownBlock.Text((heading ?: this).inlineText(source), heading = heading != null))
    }
}

private fun ASTNode.tableCells(source: String): List<AnnotatedString> = children
    .filter { it.type == GFMTokenTypes.CELL }
    .map { it.inlineText(source) }

private fun ASTNode.inlineText(source: String): AnnotatedString = buildAnnotatedString {
    appendInline(this@inlineText, source)
}.let { text ->
    val start = text.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    val end = (text.text.indexOfLast { !it.isWhitespace() } + 1).coerceAtLeast(start)
    text.subSequence(start, end)
}

private fun AnnotatedString.Builder.appendInline(
    node: ASTNode,
    source: String
) {
    when (node.type) {
        MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.filter { it.type != MarkdownTokenTypes.EMPH }.forEach { appendInline(it, source) }
            }
        }

        MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.filter { it.type != MarkdownTokenTypes.EMPH }.forEach { appendInline(it, source) }
            }
        }

        MarkdownElementTypes.CODE_SPAN -> {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                node.children.filter { it.type != MarkdownTokenTypes.BACKTICK }.forEach { append(it.rawText(source)) }
            }
        }

        MarkdownElementTypes.INLINE_LINK -> {
            appendDocumentLink(node, source)
        }

        MarkdownTokenTypes.EOL -> {
            append(" ")
        }

        else -> {
            if (node.children.isEmpty()) append(node.rawText(source)) else node.children.forEach { appendInline(it, source) }
        }
    }
}

private fun ASTNode.rawText(source: String): String = source.substring(startOffset, endOffset)

private fun AnnotatedString.Builder.appendDocumentLink(
    node: ASTNode,
    source: String
) {
    val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
    val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
    if (label == null || destination == null) {
        append(node.rawText(source))
    } else {
        pushStringAnnotation("document-link", destination.rawText(source))
        label.children.drop(1).dropLast(1).forEach { appendInline(it, source) }
        pop()
    }
}
