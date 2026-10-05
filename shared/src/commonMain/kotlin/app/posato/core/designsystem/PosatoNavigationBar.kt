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
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
    largeTitleContent: (@Composable () -> Unit)? = null,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = PosatoBarInset),
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
    val barTitleReadable by remember { derivedStateOf { collapsed >= 1f } }
    val separator by remember { derivedStateOf { if (scrollState.value > 0) 1f else 0f } }
    Column(modifier.fillMaxSize()) {
        PosatoNavigationBar(
            title = title,
            backLabel = backLabel,
            onBack = onBack,
            edgeInset = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            titleAlpha = { collapsed },
            titleReadable = barTitleReadable,
            separatorAlpha = { separator },
            trailingContent = trailingContent,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(contentPadding)
                .padding(top = if (largeTitle) 0.dp else PosatoBarContentTop, bottom = PosatoSpace.Section),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            if (largeTitle) {
                Box(Modifier.graphicsLayer { alpha = 1f - collapsed }) {
                    largeTitleContent?.invoke() ?: PosatoLargeTitle(title)
                }
            }
            content()
        }
    }
}

/** The space between a navigation bar and the first content below it, the same on every screen with a bar. */
internal val PosatoBarContentTop = PosatoSpace.Large

/**
 * The side inset of every screen with a bar. The content column is capped in width and centred, so a wider window
 * adds margin around it instead of inside it, and every screen's edge stays on one line.
 */
internal val PosatoBarInset = PosatoSpace.Section

/**
 * The iOS navigation bar. The back action's mark and the trailing actions line up with the screen's content
 * edge, [edgeInset]; the title stays centred in the space they leave and truncates rather than overlapping them.
 * Its text grows with the reading size only up to a cap, as UIKit's bars do.
 */
@Composable
internal fun PosatoNavigationBar(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    edgeInset: Dp = PosatoBarInset,
    titleAlpha: () -> Float = { 1f },
    titleReadable: Boolean = true,
    separatorAlpha: () -> Float = { 0f },
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    CappedFontScale(BAR_FONT_SCALE_LIMIT) {
        Layout(
            contents = listOf(
                { if (onBack != null) NavigationBackButton(backLabel, onBack) },
                {
                    Text(
                        text = title,
                        modifier = Modifier.graphicsLayer { alpha = titleAlpha() }
                            .then(if (titleReadable) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics {}),
                        style = PosatoTypography.BarTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                { Row(verticalAlignment = Alignment.CenterVertically) { trailingContent() } },
            ),
            modifier = modifier.fillMaxWidth().heightIn(min = NavigationBarHeight).drawBehind {
                val stroke = 1.dp.toPx() / 2
                drawLine(
                    color = hairline.copy(alpha = hairline.alpha * separatorAlpha()),
                    start = Offset(0f, size.height - stroke),
                    end = Offset(size.width, size.height - stroke),
                    strokeWidth = stroke,
                )
            },
        ) { (back, titleText, trailing), constraints ->
            val width = constraints.maxWidth
            val side = Constraints(maxWidth = (width * BAR_SIDE_SHARE).toInt())
            val backPlaceable = back.firstOrNull()?.measure(side)
            val trailingPlaceable = trailing.firstOrNull()?.measure(side)
            val start = (edgeInset - PosatoSpace.Tiny).roundToPx().coerceAtLeast(0)
            val end = (edgeInset - PosatoSpace.Small).roundToPx().coerceAtLeast(0)
            val backEnd = backPlaceable?.let { start + it.width } ?: 0
            val trailingStart = trailingPlaceable?.let { width - end - it.width } ?: width
            val reserved = maxOf(backEnd, width - trailingStart) + BarTitleGap.roundToPx()
            val titlePlaceable = titleText.first().measure(Constraints(maxWidth = (width - 2 * reserved).coerceAtLeast(0)))
            val height = maxOf(
                NavigationBarHeight.roundToPx(),
                titlePlaceable.height,
                backPlaceable?.height ?: 0,
                trailingPlaceable?.height ?: 0,
            )
            layout(width, height) {
                backPlaceable?.placeRelative(start, (height - backPlaceable.height) / 2)
                titlePlaceable.placeRelative((width - titlePlaceable.width) / 2, (height - titlePlaceable.height) / 2)
                trailingPlaceable?.placeRelative(trailingStart, (height - trailingPlaceable.height) / 2)
            }
        }
    }
}

@Composable
private fun NavigationBackButton(
    backLabel: String?,
    onBack: () -> Unit,
) {
    val label = backLabel ?: "Back"
    PosatoBarButton(
        onClick = onBack,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = if (backLabel == null) label else "Back to $label"
            role = Role.Button
            onClick {
                onBack()
                true
            }
        },
    ) {
        PosatoBackMark()
        Text(label, style = PosatoTypography.BarAction, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Lets text in [content] follow the reading size only up to [limit], for bars whose height the platform fixes. */
@Composable
internal fun CappedFontScale(
    limit: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(limit)), content = content)
}

/** A navigation bar button: tinted text that dims while pressed, without a ripple or a container. */
@Composable
internal fun PosatoBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val keyboardFocused by interaction.collectIsKeyboardFocusedAsState()
    Row(
        modifier = modifier.heightIn(min = PosatoSize.Control).widthIn(min = PosatoSize.Control)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(
                when {
                    !enabled -> PosatoControlDefaults.DISABLED_ALPHA
                    pressed -> PosatoControlDefaults.PRESSED_ALPHA
                    else -> 1f
                },
            )
            .keyboardFocusRing({ keyboardFocused }, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
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
        style = PosatoTypography.LargeTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The line under a large title that carries the screen's voice, quieter than the title. */
@Composable
internal fun PosatoLead(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, modifier = modifier, style = PosatoTypography.BarAction, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val NavigationBarHeight = 44.dp
private val BarTitleGap = 8.dp
private val BackChevronGap = 7.dp
private const val BAR_SIDE_SHARE = 0.4f
private const val BAR_FONT_SCALE_LIMIT = 1.3f
private val LARGE_TITLE_HANDOFF_START = 24.dp
private val LARGE_TITLE_HANDOFF_SPAN = 14.dp

@Preview(name = "Navigation bar", widthDp = 390)
@Composable
private fun PosatoNavigationBarPreview() {
    PosatoComponentPreview {
        PosatoNavigationBar(title = "Edit schedule", backLabel = "Schedules", onBack = {})
        PosatoLargeTitle("Schedules")
    }
}
