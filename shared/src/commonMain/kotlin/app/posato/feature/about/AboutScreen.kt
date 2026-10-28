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
import app.posato.generated.resources.about_body
import app.posato.generated.resources.about_licenses_detail
import app.posato.generated.resources.about_open_source
import app.posato.generated.resources.about_tagline
import app.posato.generated.resources.about_title
import app.posato.generated.resources.about_version
import app.posato.generated.resources.about_version_unavailable
import app.posato.generated.resources.licenses_title
import app.posato.generated.resources.nav_back
import app.posato.generated.resources.notification_section_title
import app.posato.generated.resources.notification_setting
import app.posato.generated.resources.notification_setting_denied
import app.posato.generated.resources.notification_setting_supporting
import app.posato.generated.resources.residence_login
import app.posato.generated.resources.residence_login_supporting
import app.posato.generated.resources.residence_quit
import app.posato.generated.resources.residence_section_title
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
    residence: ApplicationResidence? = null,
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
    val sections = listOfNotNull<@Composable () -> Unit>(
        residence?.let { host -> { ResidenceSection(host) } },
        notifications?.let { notifier -> { NotificationsSection(notifier) } },
    )
    val notificationsContent: (@Composable () -> Unit)? = sections.takeIf { it.isNotEmpty() }?.let { parts -> { parts.forEach { it() } } }
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
            title = stringResource(Res.string.about_title),
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
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text(stringResource(Res.string.nav_back)) }
        Text(stringResource(Res.string.about_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
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
    val formatted = stringResource(Res.string.about_version, version.orEmpty())
    val unavailable = stringResource(Res.string.about_version_unavailable)
    PosatoCaption(if (version == null) unavailable else formatted)
    PosatoBody(stringResource(Res.string.about_tagline))
    PosatoBody(stringResource(Res.string.about_body))
    PosatoBody(stringResource(Res.string.about_open_source))
    notificationsContent?.invoke()
    updatesContent?.invoke()
    PosatoDisclosureRow(
        onClick = onOpenLicenses,
        headlineContent = { Text(stringResource(Res.string.licenses_title), style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { PosatoCaption(stringResource(Res.string.about_licenses_detail)) },
    )
}

/** Starting at login and quitting, for a host that keeps Posato running in the background. */
@Composable
private fun ResidenceSection(residence: ApplicationResidence) {
    val opensAtLogin by residence.opensAtLogin.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoSectionHeader(titleContent = {
            Text(stringResource(Res.string.residence_section_title), style = MaterialTheme.typography.titleMedium)
        })
        PosatoSwitchRow(
            checked = opensAtLogin,
            onCheckedChange = residence::setOpensAtLogin,
            supportingContent = { PosatoCaption(stringResource(Res.string.residence_login_supporting)) },
        ) {
            Text(stringResource(Res.string.residence_login), style = MaterialTheme.typography.bodyLarge)
        }
        PosatoButton(onClick = residence::quit, style = PosatoButtonStyle.Quiet, controlId = "residence_quit") {
            Text(stringResource(Res.string.residence_quit))
        }
    }
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
        PosatoButton(onClick = onCheckNow, style = PosatoButtonStyle.Quiet, enabled = state.canCheckNow, controlId = "update_check_now") {
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
