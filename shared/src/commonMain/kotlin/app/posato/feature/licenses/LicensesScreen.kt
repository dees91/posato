package app.posato.feature.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import app.posato.core.designsystem.PosatoBarContentTop
import app.posato.core.designsystem.PosatoBarInset
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoNavigationBar
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.barColumn
import app.posato.generated.resources.Res
import app.posato.generated.resources.license_back_about
import app.posato.generated.resources.license_back_licenses
import app.posato.generated.resources.license_document_license
import app.posato.generated.resources.license_document_notice
import app.posato.generated.resources.license_document_text
import app.posato.generated.resources.license_document_third_party
import app.posato.generated.resources.license_included
import app.posato.generated.resources.license_open_failed
import app.posato.generated.resources.license_opening
import app.posato.generated.resources.licenses_title
import app.posato.generated.resources.mac_unified_try_again
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LicensesScreen(
    document: LicenseDocument?,
    onSelect: (LicenseDocument) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    barBackLabel: String? = null,
) {
    var state by remember(document) { mutableStateOf(LicensesUiState(document = document)) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(document, loadAttempt) {
        if (document == null) return@LaunchedEffect
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
        onSelect = onSelect,
        onBack = onBack,
        onRetry = { loadAttempt++ },
        barBackLabel = barBackLabel,
    )
}

@Composable
internal fun LicensesScreen(
    state: LicensesUiState,
    onSelect: (LicenseDocument) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    barBackLabel: String? = null,
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
    val title = licenseTitle(state.document)
    Column(modifier.fillMaxSize()) {
        if (barBackLabel != null) {
            PosatoNavigationBar(title = title, backLabel = barBackLabel, onBack = onBack)
        }
        val inset = if (barBackLabel !=
            null
        ) {
            Modifier.barColumn().padding(start = PosatoBarInset, end = PosatoBarInset, top = PosatoBarContentTop)
        } else {
            Modifier
        }
        val documentText = stringResource(Res.string.license_document_text)
        Column(modifier = Modifier.weight(1f).then(inset), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            if (barBackLabel == null) LicensesInlineHeader(state.document, title, onBack)
            key(state.document) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = documentText },
                    verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
                ) {
                    licenseDocumentItems(state, blocks, onSelect, onRetry)
                }
            }
        }
    }
}

private fun LazyListScope.licenseDocumentItems(
    state: LicensesUiState,
    blocks: List<LicenseMarkdownBlock>,
    onSelect: (LicenseDocument) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.document == null -> item {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                PosatoBody(stringResource(Res.string.license_included))
                Column {
                    LicenseDocument.entries.forEach { document ->
                        PosatoDisclosureRow(
                            onClick = { onSelect(document) },
                            headlineContent = { Text(licenseTitle(document), style = MaterialTheme.typography.bodyLarge) },
                        )
                    }
                }
            }
        }

        state.loadFailed -> item {
            PosatoBody(stringResource(Res.string.license_open_failed))
            PosatoButton(
                onClick = onRetry,
                style = PosatoButtonStyle.Secondary,
                controlId = "mac_unified_try_again",
            ) { Text(stringResource(Res.string.mac_unified_try_again)) }
        }

        state.text == null -> item {
            PosatoBody(stringResource(Res.string.license_opening))
        }

        else -> items(blocks) { block ->
            LicenseMarkdownContent(block, onSelect)
        }
    }
}

@Composable
private fun LicensesInlineHeader(
    document: LicenseDocument?,
    title: String,
    onBack: () -> Unit,
) {
    PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet, controlId = "license_back_about") {
        Text(if (document == null) stringResource(Res.string.license_back_about) else stringResource(Res.string.license_back_licenses))
    }
    Text(
        modifier = Modifier.semantics { heading() },
        text = title,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun licenseTitle(document: LicenseDocument?): String {
    val license = stringResource(Res.string.license_document_license)
    val notice = stringResource(Res.string.license_document_notice)
    val thirdParty = stringResource(Res.string.license_document_third_party)
    val fallback = stringResource(Res.string.licenses_title)
    return when (document) {
        LicenseDocument.LICENSE -> license
        LicenseDocument.NOTICE -> notice
        LicenseDocument.THIRD_PARTY_NOTICES -> thirdParty
        null -> fallback
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
