package app.posato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.posato.generated.resources.Res
import app.posato.generated.resources.application_shell_note
import app.posato.generated.resources.brand_promise
import app.posato.generated.resources.product_line
import app.posato.generated.resources.product_name
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ApplicationShell(): Unit {
    Surface(
        color = MaterialTheme.colors.background,
        contentColor = MaterialTheme.colors.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.product_name),
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.product_line),
                    style = MaterialTheme.typography.h4.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                    text = stringResource(Res.string.brand_promise),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.64f),
                    text = stringResource(Res.string.application_shell_note),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun PosatoTheme(content: @Composable () -> Unit): Unit {
    val colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colors = colors,
        content = content,
    )
}

private val DarkColors = androidx.compose.material.darkColors(
    background = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFF76B29E),
    surface = Color.Black,
)

private val LightColors = androidx.compose.material.lightColors(
    background = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    primary = Color(0xFF2E5D50),
    surface = Color.White,
)
