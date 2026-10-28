package app.posato.feature.targets.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.core.designsystem.PosatoItemMenu
import app.posato.core.designsystem.PosatoMenuItem
import app.posato.core.designsystem.PosatoMenuSymbol
import app.posato.core.designsystem.stringResourceForDevice
import app.posato.core.navigation.PosatoNavStack
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.transfer.ui.PauseSetTransferUiState
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_delete
import app.posato.generated.resources.action_duplicate
import app.posato.generated.resources.action_make_default
import app.posato.generated.resources.action_rename
import app.posato.generated.resources.pause_set_choices_only_computer
import app.posato.generated.resources.pause_set_choices_only_ipad
import app.posato.generated.resources.pause_set_choices_only_iphone
import app.posato.generated.resources.pause_set_choices_only_mac
import app.posato.generated.resources.pause_set_choices_only_phone
import app.posato.generated.resources.pause_sets_make_default_to_delete
import app.posato.generated.resources.pause_sets_more_named
import org.jetbrains.compose.resources.stringResource

private sealed interface PauseSetsRoute {
    data object List : PauseSetsRoute

    data class Set(
        val id: PauseSetId,
    ) : PauseSetsRoute
}

@Composable
internal fun PauseSetsDestination(
    inputs: PauseSetsInputs,
    navigation: PauseSetsNavigation,
    modifier: Modifier = Modifier,
    entryModifier: Modifier = Modifier,
    transfer: PauseSetTransferUiState? = null,
    viewModel: PauseSetsViewModel = viewModel { PauseSetsViewModel(inputs) },
) {
    val state by viewModel.uiState.collectAsState()
    val opened = navigation.openSet
    PosatoNavStack(
        if (opened == null) listOf(PauseSetsRoute.List) else listOf(PauseSetsRoute.List, PauseSetsRoute.Set(opened)),
        onBack = { navigation.openSet = null },
        modifier = modifier,
    ) { route ->
        when (route) {
            PauseSetsRoute.List -> PauseSetsScreen(
                state = state,
                onOpen = { id -> navigation.open(id) },
                onCreate = { name -> viewModel.create(name) { id -> navigation.open(id, TargetsCategory.WEBSITES) } },
                onRename = viewModel::rename,
                onMakeDefault = viewModel::makeDefault,
                onDuplicate = viewModel::duplicate,
                onDelete = viewModel::delete,
                onRetry = viewModel::retry,
                transfer = transfer,
            )

            is PauseSetsRoute.Set -> PauseSetScreen(
                modifier = entryModifier,
                inputs = inputs,
                navigation = navigation,
                setId = route.id,
                state = state,
                onRename = viewModel::rename,
                onMakeDefault = viewModel::makeDefault,
                onDuplicate = viewModel::duplicate,
                onDelete = viewModel::delete,
            )
        }
    }
}

/** One set's Websites and Apps, titled with its name, with Rename, Make default and Delete in its menu. */
@Composable
private fun PauseSetScreen(
    inputs: PauseSetsInputs,
    navigation: PauseSetsNavigation,
    setId: PauseSetId,
    state: PauseSetsUiState,
    onRename: (PauseSetId, String) -> Unit,
    onMakeDefault: (PauseSetId) -> Unit,
    onDuplicate: (PauseSetId) -> Unit,
    onDelete: (PauseSetId, PauseSetId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = state.rows.firstOrNull { candidate -> candidate.id == setId }
    var dialog by remember(setId) { mutableStateOf<PauseSetDialog?>(null) }
    TargetsScreen(
        store = inputs.store,
        applicationMappings = inputs.applicationMappings,
        browser = navigation.browserFor(setId),
        deviceLabel = stringResourceForDevice(
            Res.string.pause_set_choices_only_mac,
            Res.string.pause_set_choices_only_iphone,
            Res.string.pause_set_choices_only_ipad,
            Res.string.pause_set_choices_only_computer,
            Res.string.pause_set_choices_only_phone,
        ),
        setId = setId,
        header = TargetsHeader(
            title = row?.name.orEmpty(),
            onBack = { navigation.openSet = null },
            inUse = row?.inUse == true,
            menuContent = row?.let { current -> { PauseSetMenu(current, { dialog = it }, onMakeDefault, onDuplicate.takeIf { state.canCreate }) } },
        ),
        modifier = modifier,
    )
    when (val current = dialog) {
        is PauseSetDialog.Rename -> {
            PauseSetNameDialog(stringResource(Res.string.action_rename), current.row.name, onDismiss = { dialog = null }) { name ->
                dialog = null
                onRename(current.row.id, name)
            }
        }

        is PauseSetDialog.Delete -> {
            PauseSetDeleteDialog(current.row, state.rows, onDismiss = { dialog = null }) { moveTo ->
                dialog = null
                navigation.openSet = null
                onDelete(current.row.id, moveTo)
            }
        }

        PauseSetDialog.Create, null -> {}
    }
}

/** A set's actions: Rename, Make default unless it is the default, and Delete unless it is the default. */
@Composable
internal fun PauseSetMenu(
    row: PauseSetRow,
    onDialog: (PauseSetDialog) -> Unit,
    onMakeDefault: (PauseSetId) -> Unit,
    onDuplicate: ((PauseSetId) -> Unit)?,
) {
    val items = buildList {
        add(
            PosatoMenuItem(stringResource(Res.string.action_rename), {
                onDialog(PauseSetDialog.Rename(row))
            }, PosatoMenuSymbol.Rename, controlId = "action_rename"),
        )
        if (!row.refused) {
            // At ten sets there is no room for a copy, as New set says.
            add(
                PosatoMenuItem(
                    stringResource(Res.string.action_duplicate),
                    { onDuplicate?.invoke(row.id) },
                    PosatoMenuSymbol.Duplicate,
                    enabled = onDuplicate != null,
                    controlId = "action_duplicate",
                ),
            )
        }
        if (!row.isDefault && !row.refused) {
            add(
                PosatoMenuItem(stringResource(Res.string.action_make_default), {
                    onMakeDefault(row.id)
                }, PosatoMenuSymbol.MakeDefault, controlId = "action_make_default"),
            )
        }
        if (row.isDefault) {
            add(
                PosatoMenuItem(
                    stringResource(Res.string.action_delete),
                    {},
                    PosatoMenuSymbol.Remove,
                    detail = stringResource(Res.string.pause_sets_make_default_to_delete),
                    enabled = false,
                    controlId = "action_delete",
                ),
            )
        } else {
            add(
                PosatoMenuItem(stringResource(Res.string.action_delete), {
                    onDialog(PauseSetDialog.Delete(row))
                }, PosatoMenuSymbol.Remove, destructive = true, controlId = "action_delete"),
            )
        }
    }
    PosatoItemMenu(stringResource(Res.string.pause_sets_more_named, row.name), items)
}
