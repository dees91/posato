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
import app.posato.core.navigation.PosatoNavStack
import app.posato.feature.sync.domain.PauseSetId

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
    deviceNoun: String,
    modifier: Modifier = Modifier,
    entryModifier: Modifier = Modifier,
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
                deviceNoun = deviceNoun,
                onOpen = { id -> navigation.open(id) },
                onCreate = { name -> viewModel.create(name) { id -> navigation.open(id, TargetsCategory.WEBSITES) } },
                onRename = viewModel::rename,
                onMakeDefault = viewModel::makeDefault,
                onDelete = viewModel::delete,
                onRetry = viewModel::retry,
            )

            is PauseSetsRoute.Set -> PauseSetScreen(
                modifier = entryModifier,
                inputs = inputs,
                navigation = navigation,
                setId = route.id,
                state = state,
                deviceNoun = deviceNoun,
                onRename = viewModel::rename,
                onMakeDefault = viewModel::makeDefault,
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
    deviceNoun: String,
    onRename: (PauseSetId, String) -> Unit,
    onMakeDefault: (PauseSetId) -> Unit,
    onDelete: (PauseSetId, PauseSetId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = state.rows.firstOrNull { candidate -> candidate.id == setId }
    var dialog by remember(setId) { mutableStateOf<PauseSetDialog?>(null) }
    TargetsScreen(
        store = inputs.store,
        applicationMappings = inputs.applicationMappings,
        browser = navigation.browserFor(setId),
        deviceLabel = "Choices for this set on this $deviceNoun only",
        setId = setId,
        header = TargetsHeader(
            title = row?.name.orEmpty(),
            onBack = { navigation.openSet = null },
            inUse = row?.inUse == true,
            menuContent = row?.let { current -> { PauseSetMenu(current, { dialog = it }, onMakeDefault) } },
        ),
        modifier = modifier,
    )
    when (val current = dialog) {
        is PauseSetDialog.Rename -> {
            PauseSetNameDialog("Rename", current.row.name, onDismiss = { dialog = null }) { name ->
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
) {
    val items = buildList {
        add(PosatoMenuItem("Rename", { onDialog(PauseSetDialog.Rename(row)) }, PosatoMenuSymbol.Rename))
        if (!row.isDefault && !row.refused) {
            add(PosatoMenuItem("Make default", { onMakeDefault(row.id) }, PosatoMenuSymbol.MakeDefault))
        }
        if (row.isDefault) {
            add(PosatoMenuItem("Delete", {}, PosatoMenuSymbol.Remove, detail = "Make another set the default to delete this one.", enabled = false))
        } else {
            add(PosatoMenuItem("Delete", { onDialog(PauseSetDialog.Delete(row)) }, PosatoMenuSymbol.Remove, destructive = true))
        }
    }
    PosatoItemMenu("More actions for ${row.name}", items)
}
