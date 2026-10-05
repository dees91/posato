package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarContentTop
import app.posato.core.designsystem.PosatoBarInset
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoNavigationBar
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.PosatoTypography
import app.posato.core.designsystem.barColumn
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.navigation.PosatoNavStack
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.choiceSet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TargetsFrame(
    header: TargetsHeader,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val barred = platformUsesCupertinoChrome && header.onBack != null
    Column(modifier.fillMaxSize()) {
        if (barred) TargetsBar(header, onDone)
        val inset = if (barred) Modifier.padding(start = PosatoBarInset, end = PosatoBarInset, top = PosatoBarContentTop) else Modifier
        val column = if (platformUsesCupertinoChrome) Modifier.barColumn() else Modifier.fillMaxWidth()
        Column(Modifier.weight(1f).then(column).then(inset), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            if (!barred) TargetsHeaderContent(header, onDone)
            content()
        }
    }
}

/** The set's navigation bar on iOS: back to the list, the set's name, Done, and the set's menu. */
@Composable
internal fun TargetsBar(
    header: TargetsHeader,
    onDone: () -> Unit,
) {
    PosatoNavigationBar(
        title = header.title,
        backLabel = "Pause sets",
        onBack = header.onBack,
        trailingContent = {
            if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) {
                PosatoBarButton(onClick = onDone) { Text("Done", style = PosatoTypography.BarAction) }
            }
            header.menuContent?.invoke()
        },
    )
}

@Composable
internal fun TargetsHeaderContent(
    header: TargetsHeader,
    onDone: () -> Unit,
) {
    header.onBack?.let { onBack ->
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back to pause sets") }
    }
    PosatoSectionHeader(
        titleContent = { Text(header.title, style = MaterialTheme.typography.headlineSmall) },
        actionContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosatoButton(onClick = onDone, style = PosatoButtonStyle.Quiet) { Text("Done") }
                header.menuContent?.invoke()
            }
        },
    )
}

/** The website editor as its own screen on iOS: a bar back to the set, then the field and its actions. */
@Composable
internal fun WebsiteEditorScreen(
    setTitle: String,
    state: TargetsUiState,
    browser: TargetsBrowserState,
    callbacks: TargetsCallbacks,
) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        // As on the set's screen, Done appears while the keyboard is up, so the tab bar beneath it can come back.
        PosatoNavigationBar(
            title = "Edit website",
            backLabel = setTitle,
            onBack = callbacks.onCancelDomainEdit,
            trailingContent = {
                if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) {
                    PosatoBarButton(onClick = { focus.clearFocus() }) { Text("Done", style = PosatoTypography.BarAction) }
                }
            },
        )
        Column(Modifier.barColumn().padding(start = PosatoBarInset, end = PosatoBarInset, top = PosatoBarContentTop)) {
            rememberLastPresent(state.editingDomain)?.let { domain ->
                WebsiteEditor(
                    state,
                    browser.editorDraft(state.domainEditorSession, domain),
                    callbacks.onSubmitDomain,
                    callbacks.onCancelDomainEdit,
                    showsTitle = false,
                )
            }
        }
    }
}
