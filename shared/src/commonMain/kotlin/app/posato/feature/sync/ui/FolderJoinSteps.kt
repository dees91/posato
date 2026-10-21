package app.posato.feature.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.posato.core.designsystem.PosatoActivityIndicator
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTextField
import app.posato.generated.resources.Res
import app.posato.generated.resources.sync_folder_change_short
import app.posato.generated.resources.sync_folder_code_entered
import app.posato.generated.resources.sync_folder_code_entered_note
import app.posato.generated.resources.sync_folder_code_label
import app.posato.generated.resources.sync_folder_code_received
import app.posato.generated.resources.sync_folder_code_received_note
import app.posato.generated.resources.sync_folder_code_where
import app.posato.generated.resources.sync_folder_create
import app.posato.generated.resources.sync_folder_create_hint
import app.posato.generated.resources.sync_folder_join
import app.posato.generated.resources.sync_folder_joining
import app.posato.generated.resources.sync_folder_linking
import app.posato.generated.resources.sync_folder_or_type
import app.posato.generated.resources.sync_folder_reading
import app.posato.generated.resources.sync_folder_removed_copy
import app.posato.generated.resources.sync_folder_scan_hint
import app.posato.generated.resources.sync_folder_step_create
import app.posato.generated.resources.sync_folder_step_done
import app.posato.generated.resources.sync_folder_step_folder
import app.posato.generated.resources.sync_folder_step_folder_done
import app.posato.generated.resources.sync_folder_step_folder_hint
import app.posato.generated.resources.sync_folder_step_folder_hint_code
import app.posato.generated.resources.sync_folder_step_number
import app.posato.generated.resources.sync_folder_step_scan
import app.posato.generated.resources.sync_folder_step_type
import app.posato.generated.resources.sync_folder_type_hint
import app.posato.generated.resources.sync_folder_use_another_code
import app.posato.generated.resources.sync_folder_waiting_offer
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Joining a folder workspace as two steps a person may take in either order: choose the shared folder, and scan the
 * other device's QR code with the phone's own camera or type its code. The device joins as soon as both are done.
 */
@Composable
internal fun FolderJoinStepsView(
    state: SyncBootstrapUiState,
    folder: String?,
    running: Boolean,
) {
    val joining = state.joining
    val code = joining.pendingCode
    val steps = folderJoinSteps(folder, joining.folderContents, code, joining.error)
    JoinEffects(state, folder, code, steps)
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
        JoinStep(number = 1, done = steps.folderChosen, active = !steps.folderChosen) {
            if (folder != null) {
                StepTitle(stringResource(Res.string.sync_folder_step_folder_done))
                FolderChip(folder, onChange = state::clearFolder.takeUnless { running })
            } else {
                StepTitle(stringResource(Res.string.sync_folder_step_folder))
                val hint = if (code != null) Res.string.sync_folder_step_folder_hint_code else Res.string.sync_folder_step_folder_hint
                PosatoCaption(stringResource(hint))
                FolderChooser(state, running, primary = true)
            }
        }
        CodeStepView(state, steps, running)
    }
}

/**
 * Reads the chosen folder, again every few seconds while it offers to create (it may still be syncing in), and joins
 * once both steps are done, only while Posato is in front: a join waiting in the background would never be seen.
 */
@Composable
private fun JoinEffects(
    state: SyncBootstrapUiState,
    folder: String?,
    code: PendingCode?,
    steps: FolderJoinSteps,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val creating = steps.code == CodeStep.Create
    LaunchedEffect(folder) { if (folder != null) state.readFolder() }
    LaunchedEffect(folder, creating) {
        while (creating) {
            delay(CREATE_REREAD_MILLIS)
            state.readFolder()
        }
    }
    LaunchedEffect(folder, code) {
        if (!steps.joinsNow || code == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (state.joining.pendingCode === code) state.join(code)
        }
    }
}

@Composable
private fun CodeStepView(
    state: SyncBootstrapUiState,
    steps: FolderJoinSteps,
    running: Boolean,
) {
    val scans = state.folderControls.scansCodes
    val title = stringResource(if (scans) Res.string.sync_folder_step_scan else Res.string.sync_folder_step_type)
    when (val step = steps.code) {
        CodeStep.Reading -> JoinStep(number = 2, done = false, active = false) {
            StepTitle(title)
            Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small), verticalAlignment = Alignment.CenterVertically) {
                PosatoActivityIndicator()
                PosatoCaption(stringResource(Res.string.sync_folder_reading))
            }
        }

        CodeStep.RemovedCopy -> JoinStep(number = 2, done = false, active = true) {
            StepTitle(title)
            PosatoCaption(stringResource(Res.string.sync_folder_removed_copy), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        CodeStep.Create -> JoinStep(number = 2, done = false, active = true) {
            StepTitle(stringResource(Res.string.sync_folder_step_create))
            PosatoCaption(stringResource(Res.string.sync_folder_create_hint))
            PosatoCaption(stringResource(Res.string.sync_folder_linking))
            PosatoButton(onClick = state::sync, style = PosatoButtonStyle.Primary, enabled = !running) {
                Text(stringResource(Res.string.sync_folder_create))
            }
        }

        is CodeStep.Enter -> JoinStep(number = 2, done = false, active = steps.folderChosen, dimmed = false) {
            StepTitle(title)
            CodeEntry(state, step, scans, running)
        }

        is CodeStep.Ready -> JoinStep(number = 2, done = true, active = false) {
            ReadyCode(state, step, steps.folderChosen)
        }
    }
}

@Composable
private fun ColumnScope.CodeEntry(
    state: SyncBootstrapUiState,
    step: CodeStep.Enter,
    scans: Boolean,
    running: Boolean,
) {
    if (scans) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(PosatoSpace.Hairline, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .padding(PosatoSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosatoIcon(PosatoIcons.Phone, null)
            Text(stringResource(Res.string.sync_folder_scan_hint), style = MaterialTheme.typography.bodyMedium)
        }
        PosatoCaption(stringResource(Res.string.sync_folder_or_type))
    } else {
        PosatoCaption(stringResource(Res.string.sync_folder_type_hint))
    }
    val field = rememberTextFieldState(state.joining.draft)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { state.joining.draft = it } }
    val error = step.error?.joinMessage()?.let { stringResource(it) }
    // A code that cannot work is spoken at once, since the field it belongs to may not have focus.
    Box(Modifier.semantics { if (error != null) liveRegion = LiveRegionMode.Assertive }) {
        PosatoTextField(
            state = field,
            label = stringResource(Res.string.sync_folder_code_label),
            enabled = !running,
            errorMessage = error,
            onSubmit = { state.joining.enterCode(field.text.toString()) },
        )
    }
    PosatoButton(onClick = { state.joining.enterCode(field.text.toString()) }, style = PosatoButtonStyle.Primary, enabled = !running) {
        Text(stringResource(Res.string.sync_folder_join))
    }
    PosatoCaption(stringResource(Res.string.sync_folder_code_where))
}

@Composable
private fun ColumnScope.ReadyCode(
    state: SyncBootstrapUiState,
    step: CodeStep.Ready,
    folderChosen: Boolean,
) {
    val scanned = step.source == CodeSource.SCANNED
    StepTitle(stringResource(if (scanned) Res.string.sync_folder_code_received else Res.string.sync_folder_code_entered))
    PosatoCaption(stringResource(if (scanned) Res.string.sync_folder_code_received_note else Res.string.sync_folder_code_entered_note))
    val activity = when {
        !folderChosen -> null
        state.checking -> Res.string.sync_folder_joining
        state.joining.waitingForOffer -> Res.string.sync_folder_waiting_offer
        else -> null
    }
    // One live container whose sentence changes, so a screen reader hears each change once.
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (activity != null) {
            PosatoActivityIndicator()
            PosatoCaption(stringResource(activity))
        }
    }
    if (!state.checking) {
        PosatoButton(onClick = state.joining::clearCode, style = PosatoButtonStyle.Quiet) {
            Text(stringResource(Res.string.sync_folder_use_another_code))
        }
    }
}

/** One numbered step: a marker that turns into a check once done, and the step's content beside it. */
@Composable
private fun JoinStep(
    number: Int,
    done: Boolean,
    active: Boolean,
    dimmed: Boolean = !done && !active,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (dimmed) DIMMED_ALPHA else 1f),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
    ) {
        StepMarker(number, done, active)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small), content = content)
    }
}

@Composable
private fun StepMarker(
    number: Int,
    done: Boolean,
    active: Boolean,
) {
    val palette = MaterialTheme.colorScheme
    val description = if (done) stringResource(Res.string.sync_folder_step_done) else stringResource(Res.string.sync_folder_step_number, number)
    Box(
        modifier = Modifier
            .size(MARKER_SIZE)
            .background(if (done) palette.primary else palette.surface, CircleShape)
            .border(MARKER_BORDER, if (done || active) palette.primary else palette.outline, CircleShape)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            PosatoIcon(PosatoIcons.Check, null, Modifier.size(MARKER_ICON), tint = palette.onPrimary)
        } else {
            Text(number.toString(), color = if (active) palette.primary else palette.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

/** The chosen folder by its own name, with where it is underneath; [onChange] is absent once it cannot change. */
@Composable
internal fun FolderChip(
    folder: String,
    onChange: (() -> Unit)?,
) {
    val name = folder.trimEnd('/').substringAfterLast('/').ifEmpty { folder }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(PosatoSpace.Medium),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosatoIcon(PosatoIcons.Folder, null)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            PosatoCaption(folder)
        }
        onChange?.let {
            PosatoButton(onClick = it, style = PosatoButtonStyle.Quiet) { Text(stringResource(Res.string.sync_folder_change_short)) }
        }
    }
}

private const val DIMMED_ALPHA = 0.5f
private const val CREATE_REREAD_MILLIS = 5_000L
private val MARKER_SIZE = 24.dp
private val MARKER_BORDER = 1.5.dp
private val MARKER_ICON = 16.dp
