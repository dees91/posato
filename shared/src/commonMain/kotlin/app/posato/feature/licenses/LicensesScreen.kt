package app.posato.feature.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.generated.resources.Res
import kotlinx.coroutines.CancellationException

@Composable
internal fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(LicensesUiState()) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.document, loadAttempt) {
        val document = state.document ?: return@LaunchedEffect
        state = LicensesUiState(document = document)
        state = try {
            LicensesUiState(document = document, text = Res.readBytes(document.resourcePath).decodeToString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LicensesUiState(document = document, loadFailed = true)
        }
    }
    LicensesScreen(
        modifier = modifier,
        state = state,
        onSelect = { state = LicensesUiState(document = it) },
        onBack = { if (state.document == null) onBack() else state = LicensesUiState() },
        onRetry = { loadAttempt++ },
    )
}

@Composable
internal fun LicensesScreen(
    state: LicensesUiState,
    onSelect: (LicenseDocument) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(state.text, state.document) {
        val text = state.text.orEmpty()
        if (state.document == LicenseDocument.THIRD_PARTY_NOTICES) {
            parseLicenseMarkdown(text).filterIndexed { index, block ->
                index != 0 || block !is LicenseMarkdownBlock.Text || !block.heading || block.content.text != state.document.title
            }
        } else {
            text.split("\n\n").map { LicenseMarkdownBlock.Text(AnnotatedString(it)) }
        }
    }
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) {
            Text(if (state.document == null) "Back to About Posato" else "Back to licenses")
        }
        Text(
            modifier = Modifier.semantics { heading() },
            text = state.document?.title ?: "Licenses",
            style = MaterialTheme.typography.titleLarge,
        )
        key(state.document) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "Document text" },
                verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
            ) {
                when {
                    state.document == null -> item {
                        PosatoBody("The license and notices included with Posato. Available offline.")
                        Column {
                            LicenseDocument.entries.forEach { document ->
                                PosatoDisclosureRow(
                                    onClick = { onSelect(document) },
                                    headlineContent = { Text(document.title, style = MaterialTheme.typography.bodyLarge) },
                                )
                            }
                        }
                    }

                    state.loadFailed -> item {
                        PosatoBody("This document could not be opened.")
                        PosatoButton(onClick = onRetry, style = PosatoButtonStyle.Secondary) { Text("Try again") }
                    }

                    state.text == null -> item {
                        PosatoBody("Opening document…")
                    }

                    else -> items(blocks) { block ->
                        LicenseMarkdownContent(block, onSelect)
                    }
                }
            }
        }
    }
}

@Preview(name = "Licenses · iPhone", widthDp = 390, heightDp = 780)
@Composable
private fun LicensesPhonePreview(
    @PreviewParameter(LicensesScreenPreviewDataProvider::class) state: LicensesUiState
) {
    PosatoTheme {
        LicensesScreen(state, {}, {}, {}, Modifier.padding(PosatoSpace.Section))
    }
}

@Preview(name = "Licenses · Mac", widthDp = 820, heightDp = 780, uiMode = 0x20)
@Composable
private fun LicensesMacPreview(
    @PreviewParameter(LicensesScreenPreviewDataProvider::class) state: LicensesUiState
) {
    PosatoTheme {
        LicensesScreen(state, {}, {}, {}, Modifier.padding(PosatoSpace.Canvas))
    }
}
