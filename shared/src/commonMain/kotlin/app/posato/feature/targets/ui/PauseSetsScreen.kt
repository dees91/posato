package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import app.posato.core.designsystem.PosatoBadge
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarScreen
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoItemList
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoPickerOption
import app.posato.core.designsystem.PosatoPickerRow
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoSwipeAction
import app.posato.core.designsystem.PosatoSwipeRow
import app.posato.core.designsystem.PosatoTextField
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.PosatoTypography
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.feature.sync.domain.PauseSetId

/** The list's title and New set: a large title with a bar button on iOS, a section header elsewhere. */
@Composable
private fun PauseSetsChrome(
    canCreate: Boolean,
    onCreate: () -> Unit,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestBody by rememberUpdatedState(body)
    val content = remember { movableContentOf { latestBody() } }
    if (platformUsesCupertinoChrome) {
        PosatoBarScreen(
            title = "Pause sets",
            modifier = modifier,
            largeTitle = true,
            contentPadding = PaddingValues(horizontal = PosatoSpace.Section),
            trailingContent = {
                PosatoBarButton(onClick = onCreate, enabled = canCreate) {
                    Text("New set", style = PosatoTypography.BarAction)
                }
            },
        ) { content() }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            PosatoSectionHeader(
                titleContent = { Text("Pause sets", style = MaterialTheme.typography.headlineSmall) },
                actionContent = {
                    PosatoButton(onClick = onCreate, enabled = canCreate) { Text("New set") }
                },
            )
            content()
        }
    }
}

@Composable
internal fun PauseSetsScreen(
    state: PauseSetsUiState,
    deviceNoun: String,
    onOpen: (PauseSetId) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (PauseSetId, String) -> Unit,
    onMakeDefault: (PauseSetId) -> Unit,
    onDelete: (PauseSetId, PauseSetId?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<PauseSetDialog?>(null) }
    val body: @Composable () -> Unit = {
        if (state.hasLoaded && !state.canCreate && !state.isSaving) {
            PosatoCaption("You can have up to 10 pause sets.")
        }
        if (state.showsUpdateNotice) {
            PosatoNotice { Text("Update Posato on your other devices to keep them in sync.") }
        }
        state.failure?.let { failure -> PauseSetsFailureNotice(failure, onRetry) }
        PosatoItemList {
            state.rows.forEach { row ->
                PauseSetListRow(
                    row = row,
                    deviceNoun = deviceNoun,
                    onOpen = { onOpen(row.id) },
                    onDialog = { dialog = it },
                    onMakeDefault = onMakeDefault,
                )
            }
        }
    }
    PauseSetsChrome(canCreate = state.canCreate, onCreate = { dialog = PauseSetDialog.Create }, modifier = modifier, body = body)
    when (val current = dialog) {
        PauseSetDialog.Create -> {
            PauseSetNameDialog("New set", "", onDismiss = { dialog = null }) { name ->
                dialog = null
                onCreate(name)
            }
        }

        is PauseSetDialog.Rename -> {
            PauseSetNameDialog("Rename", current.row.name, onDismiss = { dialog = null }) { name ->
                dialog = null
                onRename(current.row.id, name)
            }
        }

        is PauseSetDialog.Delete -> {
            PauseSetDeleteDialog(current.row, state.rows, onDismiss = { dialog = null }) { moveTo ->
                dialog = null
                onDelete(current.row.id, moveTo)
            }
        }

        null -> {}
    }
}

@Composable
private fun PauseSetsFailureNotice(
    failure: PauseSetsFailure,
    onRetry: () -> Unit,
) {
    val message = when (failure) {
        PauseSetsFailure.LOAD_FAILED -> "Pause sets could not be loaded."
        PauseSetsFailure.SAVE_FAILED -> "The change could not be saved. Nothing was changed."
        PauseSetsFailure.IN_USE -> "You can delete this set after the pause ends."
    }
    PosatoNotice(tone = PosatoTone.Critical, actionContent = { PosatoButton(onRetry, style = PosatoButtonStyle.Quiet) { Text("Reload") } }) {
        Text(message)
    }
}

@Composable
private fun PauseSetListRow(
    row: PauseSetRow,
    deviceNoun: String,
    onOpen: () -> Unit,
    onDialog: (PauseSetDialog) -> Unit,
    onMakeDefault: (PauseSetId) -> Unit,
) {
    if (platformUsesCupertinoChrome) {
        val swipeActions = buildList {
            add(PosatoSwipeAction("Rename", { onDialog(PauseSetDialog.Rename(row)) }))
            if (!row.isDefault) add(PosatoSwipeAction("Delete", { onDialog(PauseSetDialog.Delete(row)) }, destructive = true))
        }
        PosatoSwipeRow(swipeActions) { rowActions -> PauseSetRowContent(row, deviceNoun, onOpen, rowActions) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PauseSetRowContent(row, deviceNoun, onOpen, emptyList(), Modifier.weight(1f))
            PauseSetMenu(row, onDialog, onMakeDefault)
        }
    }
}

@Composable
private fun PauseSetRowContent(
    row: PauseSetRow,
    deviceNoun: String,
    onOpen: () -> Unit,
    rowActions: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
) {
    PosatoDisclosureRow(
        onClick = onOpen,
        onClickLabel = "Open",
        modifier = modifier,
        customActions = rowActions,
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small), verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                if (row.isDefault) {
                    PosatoBadge("Default", Modifier.semantics { contentDescription = "Default set" })
                }
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                PosatoCaption(row.summary(deviceNoun))
                if (row.schedules.isNotEmpty()) PosatoCaption("Used by ${row.schedules.joinToString(", ")}")
                if (row.refused) PosatoCaption("This set is over the limit of 10. Delete a set to use it.")
            }
        },
    )
}

private fun PauseSetRow.summary(deviceNoun: String): String {
    val websites = if (websiteCount == 1) "1 website" else "$websiteCount websites"
    val apps = when (applicationCount) {
        null -> null
        0 -> "Apps on this $deviceNoun: none chosen"
        else -> "Apps on this $deviceNoun: $applicationCount"
    }
    return listOfNotNull(websites, apps).joinToString(" · ")
}

@Composable
internal fun PauseSetNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val field = remember { TextFieldState(initial) }
    var failure by remember { mutableStateOf<PauseSetNameFailure?>(null) }
    val submit = {
        val (name, problem) = parsePauseSetName(field.text.toString())
        failure = problem
        name?.let(onSave)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            PosatoTextField(
                state = field,
                label = "Name",
                errorMessage = when (failure) {
                    PauseSetNameFailure.EMPTY -> "Enter a name."
                    PauseSetNameFailure.TOO_LONG -> "Use a shorter name."
                    null -> null
                },
                onSubmit = { submit() },
            )
        },
        confirmButton = { PosatoButton(onClick = { submit() }) { Text("Save") } },
        dismissButton = { PosatoButton(onClick = onDismiss, style = PosatoButtonStyle.Quiet) { Text("Cancel") } },
    )
}

@Composable
internal fun PauseSetDeleteDialog(
    row: PauseSetRow,
    rows: List<PauseSetRow>,
    onDismiss: () -> Unit,
    onDelete: (PauseSetId?) -> Unit,
) {
    val targets = rows.filter { other -> other.id != row.id && !other.refused }
    var moveTo by remember { mutableStateOf(targets.firstOrNull { other -> other.isDefault }?.id ?: targets.firstOrNull()?.id) }
    val blocked = row.inUse
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${row.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                when {
                    blocked -> {
                        Text("You can delete this set after the pause ends.")
                    }

                    row.schedules.isEmpty() -> {
                        Text("Its websites and this device's app choices for it are deleted.")
                    }

                    else -> {
                        Text("These schedules use it: ${row.schedules.joinToString(", ")}. Change their set, then delete.")
                        PauseSetChoiceList(targets, moveTo) { moveTo = it }
                    }
                }
            }
        },
        confirmButton = {
            PosatoButton(
                onClick = { onDelete(moveTo.takeIf { row.schedules.isNotEmpty() }) },
                enabled = !blocked && (row.schedules.isEmpty() || moveTo != null),
                style = PosatoButtonStyle.Destructive,
            ) { Text(if (row.schedules.isEmpty()) "Delete" else "Change their set and delete") }
        },
        dismissButton = { PosatoButton(onClick = onDismiss, style = PosatoButtonStyle.Quiet) { Text("Cancel") } },
    )
}

/** A list of sets to pick one from, with their counts; the chosen one is marked. */
@Composable
internal fun PauseSetChoiceList(
    rows: List<PauseSetRow>,
    selected: PauseSetId?,
    onSelect: (PauseSetId) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = row.id == selected, role = Role.RadioButton) { onSelect(row.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
            ) {
                RadioButton(selected = row.id == selected, onClick = null)
                Column {
                    Text(if (row.isDefault) "${row.name} (default)" else row.name)
                    PosatoCaption(if (row.websiteCount == 1) "1 website" else "${row.websiteCount} websites")
                }
            }
        }
    }
}

/** The set choice Session and the schedule editor offer: every live set with its count, the default marked. */
@Composable
internal fun PauseSetPickerRow(
    rows: List<PauseSetRow>,
    selected: PauseSetId?,
    onChoose: (PauseSetId) -> Unit,
    placeholder: String,
) {
    PosatoPickerRow(
        label = "Pause set",
        options = rows.map { row ->
            val count = if (row.websiteCount == 1) "1 website" else "${row.websiteCount} websites"
            PosatoPickerOption(row.name, if (row.isDefault) "Default · $count" else count)
        },
        selected = rows.indexOfFirst { row -> row.id == selected }.takeIf { it >= 0 },
        onSelect = { index -> onChoose(rows[index].id) },
        placeholder = placeholder,
    )
}
