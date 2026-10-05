package app.posato.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.posato.core.designsystem.PosatoBarScreen
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoSwitchRow
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.notifications.NotificationPermission
import app.posato.feature.notifications.SessionNotifier
import app.posato.generated.resources.Res
import app.posato.generated.resources.notification_section_title
import app.posato.generated.resources.notification_setting
import app.posato.generated.resources.notification_setting_denied
import app.posato.generated.resources.notification_setting_supporting
import app.posato.generated.resources.update_automatic_checks
import app.posato.generated.resources.update_automatic_checks_supporting
import app.posato.generated.resources.update_check_now
import app.posato.generated.resources.update_section_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AboutScreen(
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    updates: ApplicationUpdates? = null,
    notifications: SessionNotifier? = null,
    barBackLabel: String? = null,
    barRoot: Boolean = false,
) {
    val version = remember { applicationVersion() }
    val updatesContent: (@Composable () -> Unit)? = updates?.let { source ->
        val updatesState by source.state.collectAsState()
        if (updatesState.available) {
            { UpdatesSection(updatesState, source::setAutomaticChecks, source::checkNow) }
        } else {
            null
        }
    }
    val notificationsContent: (@Composable () -> Unit)? = notifications?.let { notifier ->
        { NotificationsSection(notifier) }
    }
    AboutScreen(version, onOpenLicenses, onBack, modifier, barBackLabel, barRoot, notificationsContent, updatesContent)
}

@Composable
private fun NotificationsSection(notifier: SessionNotifier) {
    val settings by notifier.settings.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(notifier) { notifier.refreshPermission() }
    val denied = settings.permission == NotificationPermission.DENIED
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoSectionHeader(titleContent = {
            Text(stringResource(Res.string.notification_section_title), style = MaterialTheme.typography.titleMedium)
        })
        PosatoSwitchRow(
            checked = settings.enabled && !denied,
            onCheckedChange = { enabled -> scope.launch { notifier.setEnabled(enabled) } },
            enabled = !denied,
            supportingContent = {
                PosatoCaption(stringResource(if (denied) Res.string.notification_setting_denied else Res.string.notification_setting_supporting))
            },
        ) {
            Text(stringResource(Res.string.notification_setting), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun AboutScreen(
    version: String?,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    barBackLabel: String? = null,
    barRoot: Boolean = false,
    notificationsContent: (@Composable () -> Unit)? = null,
    updatesContent: (@Composable () -> Unit)? = null,
) {
    // With a bar, About is either pushed over a destination and leads back to it, or, chosen in the iPad's sidebar,
    // a root screen under its own large title.
    if (barBackLabel != null || barRoot) {
        PosatoBarScreen(
            title = "About Posato",
            modifier = modifier,
            largeTitle = barRoot,
            backLabel = barBackLabel,
            onBack = onBack.takeUnless { barRoot },
        ) {
            AboutBody(version, onOpenLicenses, updatesContent, notificationsContent)
        }
        return
    }
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back") }
        Text("About Posato", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            AboutBody(version, onOpenLicenses, updatesContent, notificationsContent)
        }
    }
}

@Composable
private fun AboutBody(
    version: String?,
    onOpenLicenses: () -> Unit,
    updatesContent: (@Composable () -> Unit)?,
    notificationsContent: (@Composable () -> Unit)?,
) {
    PosatoCaption(version?.let { "Version $it" } ?: "Version unavailable")
    PosatoBody("A little space. For what matters.")
    PosatoBody("Posato helps you step away from selected websites and apps for a while. A quiet pause between impulse and action.")
    PosatoBody("Open source. No Posato account, analytics, or Posato-operated server.")
    notificationsContent?.invoke()
    updatesContent?.invoke()
    PosatoDisclosureRow(
        onClick = onOpenLicenses,
        headlineContent = { Text("Licenses", style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { PosatoCaption("License and third-party notices") },
    )
}

@Composable
private fun UpdatesSection(
    state: ApplicationUpdatesState,
    onAutomaticChecksChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoSectionHeader(titleContent = { Text(stringResource(Res.string.update_section_title), style = MaterialTheme.typography.titleMedium) })
        PosatoSwitchRow(
            checked = state.automaticChecks,
            onCheckedChange = onAutomaticChecksChange,
            supportingContent = { PosatoCaption(stringResource(Res.string.update_automatic_checks_supporting)) },
        ) {
            Text(stringResource(Res.string.update_automatic_checks), style = MaterialTheme.typography.bodyLarge)
        }
        PosatoButton(onClick = onCheckNow, style = PosatoButtonStyle.Quiet, enabled = state.canCheckNow) {
            Text(stringResource(Res.string.update_check_now))
        }
    }
}

@Preview(name = "About · iPhone", widthDp = 390, heightDp = 780)
@Composable
private fun AboutPhonePreview(
    @PreviewParameter(AboutScreenPreviewDataProvider::class) version: String?
) {
    PosatoTheme { AboutScreen(version, {}, {}, Modifier.padding(PosatoSpace.Section)) }
}

@Preview(name = "About · Mac", widthDp = 820, heightDp = 780, uiMode = 0x20)
@Composable
private fun AboutMacPreview(
    @PreviewParameter(AboutScreenPreviewDataProvider::class) version: String?
) {
    PosatoTheme {
        AboutScreen(version, {}, {}, Modifier.padding(PosatoSpace.Canvas)) {
            UpdatesSection(ApplicationUpdatesState(available = true, automaticChecks = version != null, canCheckNow = version != null), {}, {})
        }
    }
}
