package app.posato.feature.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDivider
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace

@Composable
internal fun LicenseMarkdownContent(
    block: LicenseMarkdownBlock,
    onSelect: (LicenseDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is LicenseMarkdownBlock.Text -> Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            SelectionContainer {
                Text(
                    text = block.content,
                    modifier = if (block.heading) Modifier.semantics { heading() } else Modifier,
                    style = if (block.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                )
            }
            DocumentLinkActions(block.content, onSelect)
        }

        is LicenseMarkdownBlock.TableRow -> LicenseTableRow(block, onSelect, modifier)
    }
}

@Composable
private fun LicenseTableRow(
    row: LicenseMarkdownBlock.TableRow,
    onSelect: (LicenseDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < PosatoSize.CompactBreakpoint
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            if (compact) {
                row.cells.forEachIndexed { index, cell ->
                    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                        PosatoCaption(row.headers.getOrNull(index)?.text.orEmpty())
                        SelectionContainer { Text(cell, style = MaterialTheme.typography.bodyMedium) }
                        DocumentLinkActions(cell, onSelect)
                    }
                }
            } else {
                if (row.first) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
                        row.headers.forEachIndexed { index, header ->
                            Text(
                                text = header,
                                modifier = Modifier.weight(if (index == 0) COMPONENT_COLUMN_WEIGHT else 1f).semantics { heading() },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
                    row.cells.forEachIndexed { index, cell ->
                        Column(Modifier.weight(if (index == 0) COMPONENT_COLUMN_WEIGHT else 1f)) {
                            SelectionContainer { Text(cell, style = MaterialTheme.typography.bodyMedium) }
                            DocumentLinkActions(cell, onSelect)
                        }
                    }
                }
            }
            PosatoDivider()
        }
    }
}

@Composable
private fun DocumentLinkActions(
    text: AnnotatedString,
    onSelect: (LicenseDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        text.getStringAnnotations("document-link", 0, text.length).forEach { link ->
            val document = LicenseDocument.entries.firstOrNull { it.resourcePath.substringAfterLast('/') == link.item }
            if (document != null) {
                PosatoButton(onClick = { onSelect(document) }, style = PosatoButtonStyle.Quiet) {
                    Text("Read ${text.subSequence(link.start, link.end)}")
                }
            }
        }
    }
}

private const val COMPONENT_COLUMN_WEIGHT = 3f
