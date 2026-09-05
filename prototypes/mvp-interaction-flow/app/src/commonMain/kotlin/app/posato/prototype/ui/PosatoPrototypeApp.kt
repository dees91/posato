package app.posato.prototype.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import app.posato.prototype.PrototypeControl
import app.posato.prototype.PrototypeUiState
import app.posato.prototype.PrototypeViewModel
import app.posato.prototype.designsystem.PosatoPrototypeTheme
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypePlatform

@Composable
fun PosatoPrototypeApp(
    viewModel: PrototypeViewModel,
    modifier: Modifier = Modifier,
    controls: PrototypeControlsState = rememberPrototypeControlsState()
) {
    val state by viewModel.uiState.collectAsState()
    PosatoPrototypeApp(
        modifier = modifier,
        controls = controls,
        state = state,
        onAction = viewModel::dispatch,
        onControl = viewModel::control,
        onReviewDuration = viewModel::reviewDuration,
    )
}

@Composable
fun PosatoPrototypeApp(
    state: PrototypeUiState,
    onAction: (PrototypeAction) -> Unit,
    onControl: (PrototypeControl) -> Unit,
    onReviewDuration: (String) -> Unit,
    modifier: Modifier = Modifier,
    controls: PrototypeControlsState = rememberPrototypeControlsState()
) {
    var hasOpenedControls by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(PrototypeDisplayOptions()) }
    val brandFocus = remember { FocusRequester() }
    val openControls = {
        controls.isOpen = true
        hasOpenedControls = true
    }
    val density = LocalDensity.current
    val fontScale = density.fontScale * if (options.enlargedText) PrototypeUiTokens.ENLARGED_TEXT_SCALE else 1f
    val dark = when (options.appearance) {
        PrototypeAppearance.System -> isSystemInDarkTheme()
        PrototypeAppearance.Light -> false
        PrototypeAppearance.Dark -> true
    }
    LaunchedEffect(controls.isOpen) {
        if (!controls.isOpen && (hasOpenedControls || state.prototype.platform == PrototypePlatform.Mac)) {
            brandFocus.requestFocus()
        }
    }
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        PosatoPrototypeTheme(darkTheme = dark, highContrast = options.highContrast) {
            Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Box(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                ) {
                    key(state.resetKey) {
                        PrototypeApplicationLayout(state.prototype, brandFocus, openControls, onAction, onReviewDuration)
                    }
                    if (controls.isOpen) {
                        PrototypeControlsOverlay(
                            state = state,
                            options = options,
                            onOptions = { options = it },
                            onAction = {
                                onAction(it)
                                controls.isOpen = false
                            },
                            onControl = {
                                onControl(it)
                                controls.isOpen = false
                            },
                            onDismiss = { controls.isOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PrototypeCompactPreview(
    @PreviewParameter(PrototypePreviewDataProvider::class) preview: PrototypePreviewCase
) {
    PosatoPrototypeApp(preview.state, {}, {}, {})
}

@Preview(widthDp = 1060, heightDp = 780)
@Composable
private fun PrototypeExpandedPreview(
    @PreviewParameter(PrototypePreviewDataProvider::class) preview: PrototypePreviewCase
) {
    PosatoPrototypeApp(preview.state, {}, {}, {})
}
