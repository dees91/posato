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
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
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
import app.posato.core.designsystem.PosatoAlert
import app.posato.core.designsystem.PosatoAlertAction
import app.posato.core.designsystem.PosatoAlertField
import app.posato.core.designsystem.PosatoAlertRole
import app.posato.core.designsystem.PosatoBadge
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarInset
import app.posato.core.designsystem.PosatoBarScreen
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoItemList
import app.posato.core.designsystem.PosatoItemMenu
import app.posato.core.designsystem.PosatoMenuItem
import app.posato.core.designsystem.PosatoMenuSymbol
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
import app.posato.core.designsystem.stringResourceForDevice
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.transfer.ui.PauseSetTransferDialogs
import app.posato.feature.transfer.ui.PauseSetTransferUiState
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_delete
import app.posato.generated.resources.action_duplicate
import app.posato.generated.resources.action_export_pause_sets
import app.posato.generated.resources.action_import_ellipsis
import app.posato.generated.resources.action_more_pause_sets
import app.posato.generated.resources.action_new_set
import app.posato.generated.resources.action_open
import app.posato.generated.resources.action_reload_short
import app.posato.generated.resources.action_rename
import app.posato.generated.resources.action_save_short
import app.posato.generated.resources.count_websites
import app.posato.generated.resources.nav_pause_sets
import app.posato.generated.resources.pause_set_apps_count_computer
import app.posato.generated.resources.pause_set_apps_count_ipad
import app.posato.generated.resources.pause_set_apps_count_iphone
import app.posato.generated.resources.pause_set_apps_count_mac
import app.posato.generated.resources.pause_set_apps_count_phone
import app.posato.generated.resources.pause_set_apps_none_computer
import app.posato.generated.resources.pause_set_apps_none_ipad
import app.posato.generated.resources.pause_set_apps_none_iphone
import app.posato.generated.resources.pause_set_apps_none_mac
import app.posato.generated.resources.pause_set_apps_none_phone
import app.posato.generated.resources.pause_sets_default
import app.posato.generated.resources.pause_sets_default_count
import app.posato.generated.resources.pause_sets_default_set
import app.posato.generated.resources.pause_sets_delete_after
import app.posato.generated.resources.pause_sets_delete_apps
import app.posato.generated.resources.pause_sets_delete_named
import app.posato.generated.resources.pause_sets_delete_schedules
import app.posato.generated.resources.pause_sets_label
import app.posato.generated.resources.pause_sets_limit
import app.posato.generated.resources.pause_sets_load_failed
import app.posato.generated.resources.pause_sets_move_delete
import app.posato.generated.resources.pause_sets_name_empty
import app.posato.generated.resources.pause_sets_name_label
import app.posato.generated.resources.pause_sets_name_long
import app.posato.generated.resources.pause_sets_save_failed
import app.posato.generated.resources.pause_sets_update
import app.posato.generated.resources.pause_sets_used_by
import app.posato.generated.resources.presence_first_close_ok
import app.posato.generated.resources.schedule_set_over_limit
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The list's title and New set: a large title with a bar button on iOS, a section header elsewhere. */
@Composable
private fun PauseSetsChrome(
    canCreate: Boolean,
    onCreate: () -> Unit,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    transfer: PauseSetTransferUiState? = null,
) {
    val more: @Composable () -> Unit = { transfer?.takeIf { it.available }?.let { state -> PauseSetsMoreMenu(state) } }
    val latestBody by rememberUpdatedState(body)
    val content = remember { movableContentOf { latestBody() } }
    if (platformUsesCupertinoChrome) {
        PosatoBarScreen(
            title = stringResource(Res.string.nav_pause_sets),
            modifier = modifier,
            largeTitle = true,
            contentPadding = PaddingValues(horizontal = PosatoBarInset),
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    more()
                    PosatoBarButton(onClick = onCreate, enabled = canCreate, controlId = "action_new_set") {
                        Text(stringResource(Res.string.action_new_set), style = PosatoTypography.BarAction)
                    }
                }
            },
        ) { content() }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
            PosatoSectionHeader(
                titleContent = { Text(stringResource(Res.string.nav_pause_sets), style = MaterialTheme.typography.headlineSmall) },
                actionContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                        PosatoButton(onClick = onCreate, enabled = canCreate, controlId = "action_new_set") {
                            Text(stringResource(Res.string.action_new_set))
                        }
                        more()
                    }
                },
            )
            content()
        }
    }
}

@Composable
internal fun PauseSetsScreen(
    state: PauseSetsUiState,
    onOpen: (PauseSetId) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (PauseSetId, String) -> Unit,
    onMakeDefault: (PauseSetId) -> Unit,
    onDuplicate: (PauseSetId) -> Unit,
    onDelete: (PauseSetId, PauseSetId?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    transfer: PauseSetTransferUiState? = null,
) {
    var dialog by remember { mutableStateOf<PauseSetDialog?>(null) }
    val body: @Composable () -> Unit = {
        if (state.hasLoaded && !state.canCreate && !state.isSaving) {
            PosatoCaption(stringResource(Res.string.pause_sets_limit))
        }
        if (state.showsUpdateNotice) {
            PosatoNotice { Text(stringResource(Res.string.pause_sets_update)) }
        }
        state.failure?.let { failure -> PauseSetsFailureNotice(failure, onRetry) }
        PosatoItemList {
            state.rows.forEach { row ->
                PauseSetListRow(
                    row = row,
                    onOpen = { onOpen(row.id) },
                    onDialog = { dialog = it },
                    onMakeDefault = onMakeDefault,
                    onDuplicate = onDuplicate.takeIf { state.canCreate },
                )
            }
        }
    }
    PauseSetsChrome(canCreate = state.canCreate, onCreate = { dialog = PauseSetDialog.Create }, modifier = modifier, body = body, transfer = transfer)
    transfer?.let { PauseSetTransferDialogs(it) }
    when (val current = dialog) {
        PauseSetDialog.Create -> {
            PauseSetNameDialog(stringResource(Res.string.action_new_set), "", onDismiss = { dialog = null }) { name ->
                dialog = null
                onCreate(name)
            }
        }

        is PauseSetDialog.Rename -> {
            PauseSetNameDialog(stringResource(Res.string.action_rename), current.row.name, onDismiss = { dialog = null }) { name ->
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

/** Export and import, from the "…" beside New set. */
@Composable
private fun PauseSetsMoreMenu(transfer: PauseSetTransferUiState) {
    PosatoItemMenu(
        stringResource(Res.string.action_more_pause_sets),
        listOf(
            PosatoMenuItem(
                stringResource(Res.string.action_export_pause_sets),
                transfer::startExport,
                PosatoMenuSymbol.Export,
                controlId = "action_export_pause_sets",
            ),
            PosatoMenuItem(
                stringResource(Res.string.action_import_ellipsis),
                transfer::startImport,
                PosatoMenuSymbol.Import,
                controlId = "action_import_ellipsis",
            ),
        ),
    )
}

@Composable
private fun PauseSetsFailureNotice(
    failure: PauseSetsFailure,
    onRetry: () -> Unit,
) {
    val message = when (failure) {
        PauseSetsFailure.LOAD_FAILED -> stringResource(Res.string.pause_sets_load_failed)
        PauseSetsFailure.SAVE_FAILED -> stringResource(Res.string.pause_sets_save_failed)
        PauseSetsFailure.IN_USE -> stringResource(Res.string.pause_sets_delete_after)
    }
    PosatoNotice(tone = PosatoTone.Critical, actionContent = {
        PosatoButton(onRetry, style = PosatoButtonStyle.Quiet, controlId = "action_reload_short") {
            Text(stringResource(Res.string.action_reload_short))
        }
    }) {
        Text(message)
    }
}

@Composable
private fun PauseSetListRow(
    row: PauseSetRow,
    onOpen: () -> Unit,
    onDialog: (PauseSetDialog) -> Unit,
    onMakeDefault: (PauseSetId) -> Unit,
    onDuplicate: ((PauseSetId) -> Unit)?,
) {
    if (platformUsesCupertinoChrome) {
        val swipeActions = buildList {
            add(PosatoSwipeAction(stringResource(Res.string.action_rename), { onDialog(PauseSetDialog.Rename(row)) }, controlId = "action_rename"))
            if (onDuplicate != null && !row.refused) {
                add(PosatoSwipeAction(stringResource(Res.string.action_duplicate), { onDuplicate(row.id) }, controlId = "action_duplicate"))
            }
            if (!row.isDefault) {
                add(
                    PosatoSwipeAction(stringResource(Res.string.action_delete), {
                        onDialog(PauseSetDialog.Delete(row))
                    }, destructive = true, controlId = "action_delete"),
                )
            }
        }
        // Only the iOS row carries an id: it has no menu button to find it by, and a desktop verification host would
        // announce the id instead of the row's name, which the desktop flows read.
        PosatoSwipeRow(swipeActions) { rowActions -> PauseSetRowContent(row, onOpen, rowActions, controlId = "pause_set_row") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PauseSetRowContent(row, onOpen, emptyList(), Modifier.weight(1f))
            PauseSetMenu(row, onDialog, onMakeDefault, onDuplicate)
        }
    }
}

@Composable
private fun PauseSetRowContent(
    row: PauseSetRow,
    onOpen: () -> Unit,
    rowActions: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
    controlId: String? = null,
) {
    PosatoDisclosureRow(
        onClick = onOpen,
        onClickLabel = stringResource(Res.string.action_open),
        modifier = modifier,
        customActions = rowActions,
        controlId = controlId,
        headlineContent = {
            val defaultLabel = stringResource(Res.string.pause_sets_default)
            val defaultSet = stringResource(Res.string.pause_sets_default_set)
            Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small), verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                if (row.isDefault) {
                    PosatoBadge(defaultLabel, Modifier.semantics { contentDescription = defaultSet })
                }
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                PosatoCaption(row.summary())
                if (row.schedules.isNotEmpty()) PosatoCaption(stringResource(Res.string.pause_sets_used_by, row.schedules.joinToString(", ")))
                if (row.refused) PosatoCaption(stringResource(Res.string.schedule_set_over_limit))
            }
        },
    )
}

@Composable
private fun PauseSetRow.summary(): String {
    val websites = pluralStringResource(Res.plurals.count_websites, websiteCount, websiteCount)
    val none = stringResourceForDevice(
        Res.string.pause_set_apps_none_mac,
        Res.string.pause_set_apps_none_iphone,
        Res.string.pause_set_apps_none_ipad,
        Res.string.pause_set_apps_none_computer,
        Res.string.pause_set_apps_none_phone,
    )
    val counted = stringResourceForDevice(
        Res.string.pause_set_apps_count_mac,
        Res.string.pause_set_apps_count_iphone,
        Res.string.pause_set_apps_count_ipad,
        Res.string.pause_set_apps_count_computer,
        Res.string.pause_set_apps_count_phone,
        applicationCount ?: 0,
    )
    val apps = when (applicationCount) {
        null -> null
        0 -> none
        else -> counted
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
    var draft by remember { mutableStateOf(initial) }
    var failure by remember { mutableStateOf<PauseSetNameFailure?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    PosatoAlert(
        title = title,
        message = when (failure) {
            PauseSetNameFailure.EMPTY -> stringResource(Res.string.pause_sets_name_empty)
            PauseSetNameFailure.TOO_LONG -> stringResource(Res.string.pause_sets_name_long)
            null -> null
        },
        field = PosatoAlertField(draft, stringResource(Res.string.pause_sets_name_label)),
        attempt = attempt,
        onDismiss = onDismiss,
        actions = listOf(
            PosatoAlertAction(stringResource(Res.string.action_cancel), { onDismiss() }, PosatoAlertRole.Cancel, controlId = "action_cancel"),
            PosatoAlertAction(stringResource(Res.string.action_save_short), { text ->
                val (name, problem) = parsePauseSetName(text)
                draft = text
                failure = problem
                attempt++
                name?.let(onSave)
            }, controlId = "action_save_short"),
        ),
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
    val cancel = PosatoAlertAction(stringResource(Res.string.action_cancel), { onDismiss() }, PosatoAlertRole.Cancel, controlId = "action_cancel")
    val ok = stringResource(Res.string.presence_first_close_ok)
    val delete = stringResource(Res.string.action_delete)
    val inUse = stringResource(Res.string.pause_sets_delete_after)
    val appsDeleted = stringResource(Res.string.pause_sets_delete_apps)
    val schedulesUseIt = stringResource(Res.string.pause_sets_delete_schedules, row.schedules.joinToString(", "), row.name)
    val title = stringResource(Res.string.pause_sets_delete_named, row.name)
    val message: String
    val actions: List<PosatoAlertAction>
    if (row.inUse) {
        message = inUse
        actions = listOf(PosatoAlertAction(ok, { onDismiss() }, PosatoAlertRole.Cancel, controlId = "presence_first_close_ok"))
    } else if (row.schedules.isEmpty()) {
        message = appsDeleted
        actions = listOf(cancel, PosatoAlertAction(delete, { onDelete(null) }, PosatoAlertRole.Destructive, controlId = "action_delete"))
    } else {
        message = schedulesUseIt
        actions = targets.map { target ->
            key(target.id) {
                PosatoAlertAction(stringResource(Res.string.pause_sets_move_delete, target.name), {
                    onDelete(target.id)
                }, PosatoAlertRole.Destructive)
            }
        } + cancel
    }
    PosatoAlert(title = title, message = message, actions = actions, onDismiss = onDismiss)
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
        label = stringResource(Res.string.pause_sets_label),
        options = rows.map { row ->
            key(row.id) { pauseSetOption(row) }
        },
        selected = rows.indexOfFirst { row -> row.id == selected }.takeIf { it >= 0 },
        onSelect = { index -> onChoose(rows[index].id) },
        placeholder = placeholder,
    )
}

@Composable
private fun pauseSetOption(row: PauseSetRow): PosatoPickerOption {
    val count = pluralStringResource(Res.plurals.count_websites, row.websiteCount, row.websiteCount)
    val marked = stringResource(Res.string.pause_sets_default_count, count)
    return PosatoPickerOption(row.name, if (row.isDefault) marked else count)
}
