package app.posato.core.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A screen in the iOS manner: a navigation bar with the back chevron, and, for a top-level screen, a large title
 * that scrolls with the content and hands its name to the bar once it passes under it.
 */
@Composable
internal fun PosatoBarScreen(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = false,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = PosatoSpace.Section),
    trailingContent: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val collapsed by remember(largeTitle) {
        derivedStateOf {
            if (!largeTitle) {
                1f
            } else {
                val start = with(density) { LARGE_TITLE_HANDOFF_START.toPx() }
                val span = with(density) { LARGE_TITLE_HANDOFF_SPAN.toPx() }
                ((scrollState.value - start) / span).coerceIn(0f, 1f)
            }
        }
    }
    val separator by remember { derivedStateOf { if (scrollState.value > 0) 1f else 0f } }
    Column(modifier.fillMaxSize()) {
        PosatoNavigationBar(
            title = title,
            backLabel = backLabel,
            onBack = onBack,
            titleAlpha = { collapsed },
            separatorAlpha = { separator },
            trailingContent = trailingContent,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(contentPadding)
                .padding(top = if (largeTitle) 0.dp else PosatoBarContentTop, bottom = PosatoSpace.Section),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            if (largeTitle) {
                PosatoLargeTitle(title, Modifier.graphicsLayer { alpha = 1f - collapsed })
            }
            content()
        }
    }
}

/** The space between a navigation bar and the first content below it, the same on every screen with a bar. */
internal val PosatoBarContentTop = PosatoSpace.Large

@Composable
internal fun PosatoNavigationBar(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    titleAlpha: () -> Float = { 1f },
    separatorAlpha: () -> Float = { 0f },
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier.fillMaxWidth().height(NavigationBarHeight).drawBehind {
            val stroke = 1.dp.toPx() / 2
            drawLine(
                color = hairline.copy(alpha = hairline.alpha * separatorAlpha()),
                start = Offset(0f, size.height - stroke),
                end = Offset(size.width, size.height - stroke),
                strokeWidth = stroke,
            )
        },
    ) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center).widthIn(max = NavigationTitleMaxWidth).graphicsLayer { alpha = titleAlpha() }
                .semantics { heading() },
            style = NavigationTitleStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onBack != null) {
            val label = backLabel ?: "Back"
            PosatoBarButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = PosatoSpace.Tiny)
                    .clearAndSetSemantics {
                        contentDescription = if (backLabel == null) label else "Back to $label"
                        role = Role.Button
                        onClick {
                            onBack()
                            true
                        }
                    },
            ) {
                PosatoBackMark()
                Text(label, style = BarButtonStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = PosatoSpace.Small), verticalAlignment = Alignment.CenterVertically) {
            trailingContent()
        }
    }
}

/** A navigation bar button: tinted text that dims while pressed, without a ripple or a container. */
@Composable
internal fun PosatoBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier.heightIn(min = PosatoSize.Control).widthIn(min = PosatoSize.Control)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .alpha(if (pressed) PosatoControlDefaults.PRESSED_ALPHA else 1f)
            .padding(horizontal = PosatoSpace.Tiny),
        horizontalArrangement = Arrangement.spacedBy(BackChevronGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { content() }
    }
}

@Composable
internal fun PosatoLargeTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = LargeTitleStyle,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The line under a large title that carries the screen's voice, quieter than the title. */
@Composable
internal fun PosatoLead(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, modifier = modifier, style = LeadStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val NavigationBarHeight = 44.dp
private val NavigationTitleMaxWidth = 220.dp
private val BackChevronGap = 7.dp
private val LARGE_TITLE_HANDOFF_START = 24.dp
private val LARGE_TITLE_HANDOFF_SPAN = 14.dp

private val NavigationTitleStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.43).sp)
private val BarButtonStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.43).sp)
private val LeadStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.43).sp)
private val LargeTitleStyle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.37.sp)

@Preview(name = "Navigation bar", widthDp = 390)
@Composable
private fun PosatoNavigationBarPreview() {
    PosatoComponentPreview {
        PosatoNavigationBar(title = "Edit schedule", backLabel = "Schedules", onBack = {})
        PosatoLargeTitle("Schedules")
    }
}
