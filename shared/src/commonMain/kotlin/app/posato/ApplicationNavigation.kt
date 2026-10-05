package app.posato

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.posato.core.designsystem.PosatoBottomNavigation
import app.posato.core.designsystem.PosatoBottomNavigationItem
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoControlDefaults
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoDeviceLabel
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNavigationPlacement
import app.posato.core.designsystem.PosatoNavigationScaffold
import app.posato.core.designsystem.PosatoSidebarNavigationItem
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoWordmark
import app.posato.core.designsystem.windowNavigationPlacement

@Composable
internal fun ApplicationNavigationHeader(
    device: PosatoDevice,
    onOpenAbout: (() -> Unit)? = null,
    inset: Boolean = true,
) {
    val topInset = if (device == PosatoDevice.Mac) PosatoSpace.Spacious else 0.dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(if (inset) PosatoSpace.Section else 0.dp).padding(top = topInset),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosatoWordmark(Modifier.heightIn(min = PosatoSize.Control))
        if (onOpenAbout != null) {
            PosatoButton(onClick = onOpenAbout, style = PosatoButtonStyle.Quiet) { Text("About Posato") }
        }
    }
}

@Composable
internal fun ApplicationNavigation(
    placement: PosatoNavigationPlacement,
    device: PosatoDevice,
    destination: ApplicationDestination,
    showingInformation: Boolean,
    onSelect: (ApplicationDestination) -> Unit,
    onOpenAbout: () -> Unit,
) {
    when (placement) {
        PosatoNavigationPlacement.Bottom -> PosatoBottomNavigation {
            PosatoBottomNavigationItem(
                selected = destination == ApplicationDestination.SESSION,
                onClick = { onSelect(ApplicationDestination.SESSION) },
                iconContent = { PosatoIcon(PosatoIcons.Pause, null, Modifier.size(PosatoSize.LargeIcon)) },
                modifier = Modifier.weight(1f),
            ) { Text("Session") }
            PosatoBottomNavigationItem(
                selected = destination == ApplicationDestination.TARGETS,
                onClick = { onSelect(ApplicationDestination.TARGETS) },
                iconContent = { PosatoIcon(PosatoIcons.Items, null, Modifier.size(PosatoSize.LargeIcon)) },
                modifier = Modifier.weight(1f),
            ) { Text("Pause sets") }
            PosatoBottomNavigationItem(
                modifier = Modifier.weight(1f),
                selected = destination == ApplicationDestination.SCHEDULES,
                onClick = { onSelect(ApplicationDestination.SCHEDULES) },
                iconContent = { PosatoIcon(PosatoIcons.Clock, null, Modifier.size(PosatoSize.LargeIcon)) },
            ) { Text("Schedules") }
        }

        PosatoNavigationPlacement.Sidebar -> Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PosatoSpace.Medium, vertical = PosatoSpace.Small),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                PosatoSidebarNavigationItem(
                    selected = destination == ApplicationDestination.SESSION && !showingInformation,
                    onClick = { onSelect(ApplicationDestination.SESSION) },
                    iconContent = { PosatoIcon(PosatoIcons.Pause, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Session") }
                PosatoSidebarNavigationItem(
                    selected = destination == ApplicationDestination.TARGETS && !showingInformation,
                    onClick = { onSelect(ApplicationDestination.TARGETS) },
                    iconContent = { PosatoIcon(PosatoIcons.Items, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pause sets") }
                PosatoSidebarNavigationItem(
                    modifier = Modifier.fillMaxWidth(),
                    selected = destination == ApplicationDestination.SCHEDULES && !showingInformation,
                    onClick = { onSelect(ApplicationDestination.SCHEDULES) },
                    iconContent = { PosatoIcon(PosatoIcons.Clock, null) },
                ) { Text("Schedules") }
            }
            val labelInset = PosatoSpace.Medium + PosatoSize.Icon + PosatoSpace.Medium
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                PosatoDeviceLabel("On this ${device.noun}", Modifier.padding(start = labelInset))
                PosatoButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.padding(start = labelInset),
                    style = PosatoButtonStyle.Quiet,
                ) { Text("About Posato") }
            }
        }
    }
}

@Composable
internal fun ApplicationNavigationScaffold(
    device: PosatoDevice,
    destination: ApplicationDestination,
    showingInformation: Boolean,
    onSelect: (ApplicationDestination) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PosatoLayout) -> Unit,
) {
    val placement = windowNavigationPlacement(device)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val hideNavigation = placement == PosatoNavigationPlacement.Bottom && keyboardVisible
    PosatoNavigationScaffold(
        placement = placement,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing),
        headerContent = {
            if (!hideNavigation) {
                ApplicationNavigationHeader(
                    device,
                    onOpenAbout = onOpenAbout.takeUnless { showingInformation || placement == PosatoNavigationPlacement.Sidebar },
                )
            }
        },
        navigationContent = {
            if (!hideNavigation && (placement == PosatoNavigationPlacement.Sidebar || !showingInformation)) {
                ApplicationNavigation(placement, device, destination, showingInformation, onSelect, onOpenAbout)
            }
        },
        content = content,
    )
}

/**
 * A destination with the tab bar beneath it, in the iOS manner: the bar belongs to the screen, so a screen pushed
 * over the destinations covers it and takes it along, and the keyboard rises over it instead of removing it. Beside
 * a sidebar, [showsTabBar] is false and the destination keeps only the home indicator's space.
 */
@Composable
internal fun CupertinoTabFrame(
    device: PosatoDevice,
    destination: ApplicationDestination,
    onSelect: (ApplicationDestination) -> Unit,
    modifier: Modifier = Modifier,
    showsTabBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var barHeight by remember { mutableIntStateOf(0) }
    val keyboard = WindowInsets.ime.getBottom(density)
    val bottom = if (showsTabBar) barHeight else WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).exclude(WindowInsets.ime).getBottom(density)
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().padding(bottom = with(density) { maxOf(bottom, keyboard).toDp() }),
            contentAlignment = Alignment.TopCenter,
        ) { content() }
        if (showsTabBar) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barHeight = it.height }
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).exclude(WindowInsets.ime)),
            ) {
                ApplicationNavigation(
                    placement = PosatoNavigationPlacement.Bottom,
                    device = device,
                    destination = destination,
                    showingInformation = false,
                    onSelect = onSelect,
                    onOpenAbout = {},
                )
            }
        }
    }
}

/**
 * The iPad's sidebar in landscape: the wordmark where a large title would stand, the destinations as rows, and About
 * at the foot, on the grouped background with a hairline toward the screens.
 */
@Composable
internal fun CupertinoSidebar(
    device: PosatoDevice,
    destination: ApplicationDestination,
    showingInformation: Boolean,
    onSelect: (ApplicationDestination) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind { drawLine(hairline, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx() / 2) }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PosatoSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
    ) {
        PosatoWordmark(Modifier.padding(start = PosatoSpace.Medium, top = SidebarBarHeight, bottom = PosatoSpace.Large))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            SidebarRow(ApplicationDestination.SESSION, PosatoIcons.Pause, destination, showingInformation, onSelect)
            SidebarRow(ApplicationDestination.TARGETS, PosatoIcons.Items, destination, showingInformation, onSelect)
            SidebarRow(ApplicationDestination.SCHEDULES, PosatoIcons.Clock, destination, showingInformation, onSelect)
        }
        Spacer(Modifier.weight(1f))
        PosatoDeviceLabel("On this ${device.noun}", Modifier.padding(start = PosatoSpace.Medium))
        PosatoSidebarNavigationItem(
            selected = showingInformation,
            onClick = onOpenAbout,
            iconContent = { PosatoIcon(PosatoIcons.Info, null, Modifier.size(PosatoSize.LargeIcon)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = PosatoSpace.Small),
        ) { Text("About Posato") }
    }
}

@Composable
private fun SidebarRow(
    entry: ApplicationDestination,
    icon: ImageVector,
    destination: ApplicationDestination,
    showingInformation: Boolean,
    onSelect: (ApplicationDestination) -> Unit,
) {
    PosatoSidebarNavigationItem(
        selected = destination == entry && !showingInformation,
        onClick = { onSelect(entry) },
        iconContent = { PosatoIcon(icon, null, Modifier.size(PosatoSize.LargeIcon)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(entry.title) }
}

private val SidebarBarHeight = 44.dp
