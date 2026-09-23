package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace

@Composable
internal fun OnboardingPage(
    layout: PosatoLayout,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = layout == PosatoLayout.Compact
    val scroll = rememberScrollState()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(
        modifier = if (compact) Modifier.fillMaxSize() else Modifier.widthIn(max = PosatoSize.CompactBreakpoint).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact || !keyboardVisible) PosatoSpace.Spacious else PosatoSpace.Section),
    ) {
        Column(
            modifier = if (compact) {
                Modifier.weight(1f).verticalScroll(scroll)
            } else {
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll)
            },
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
            content = content,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
            content = actions,
        )
    }
}

@Composable
internal fun OnboardingActions(
    layout: PosatoLayout,
    content: @Composable FlowRowScope.() -> Unit,
) {
    val compact = layout == PosatoLayout.Compact
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (compact) {
            Arrangement.Center
        } else {
            Arrangement.spacedBy(PosatoSpace.Medium)
        },
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
        maxItemsInEachRow = if (compact) 1 else Int.MAX_VALUE,
        content = content,
    )
}

@Composable
internal fun OnboardingPrimaryAction(
    label: String,
    layout: PosatoLayout,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    PosatoButton(
        modifier = if (layout == PosatoLayout.Compact) Modifier.fillMaxWidth() else Modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(label)
    }
}
