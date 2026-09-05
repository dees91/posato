package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoFieldMessage
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoSelectionRow
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoTextField
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeFixtures
import app.posato.prototype.model.PrototypeState

@Composable
internal fun PrototypeDomainEditor(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = rememberTextFieldState(state.editor.originalDomain.orEmpty())
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(state.editor.sessionKey) { fieldFocus.requestFocus() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            if (state.editor.originalDomain == null) "A website to pause." else "Change this website.",
            eyebrow = "ONE EXACT DOMAIN",
            layout = layout,
            description = "Enter a domain or paste a website URL. Only the domain is kept.",
        )
        PosatoTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            state = draft,
            label = "Website domain",
            placeholder = "news.example",
            errorMessage = state.editor.domainError,
            supportingText = "Paths and query strings are not saved.",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onSubmit = { onAction(ItemAction.SaveDomain(draft.text.toString())) },
        )
        PosatoActionRow {
            PosatoButton(onClick = { onAction(ItemAction.SaveDomain(draft.text.toString())) }) { Text("Save website") }
            PosatoButton(onClick = { onAction(ItemAction.Cancel) }, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
        }
    }
}

@Composable
internal fun PrototypeApplicationPicker(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember(state.editor.sessionKey) { mutableStateOf(state.localApplications()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "Choose your applications.",
            eyebrow = "ON THIS ${state.platform.label.uppercase()}",
            layout = layout,
            description = "Select the apps you want to pause here. Your other devices keep their own choices.",
        )
        PosatoCaption("Synthetic applications · prototype picker, not an operating-system prompt.")
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            PrototypeFixtures.applications(state.platform).forEach { name ->
                PosatoSelectionRow(
                    modifier = Modifier.fillMaxWidth(),
                    checked = name in selected,
                    onCheckedChange = { checked -> selected = if (checked) selected.adding(name) else selected.removing(name) },
                ) { Text(name) }
            }
        }
        state.editor.applicationError?.let { PosatoFieldMessage(it, isError = true) }
        PosatoActionRow {
            PosatoButton(onClick = { onAction(ItemAction.SaveApplications(selected)) }) { Text("Save selection") }
            PosatoButton(onClick = { onAction(ItemAction.Cancel) }, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
        }
    }
}
