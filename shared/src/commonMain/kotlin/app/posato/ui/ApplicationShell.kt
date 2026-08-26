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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.posato.generated.resources.Res
import app.posato.generated.resources.application_shell_note
import app.posato.generated.resources.brand_promise
import app.posato.generated.resources.product_line
import app.posato.generated.resources.product_name
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ApplicationShell() {
    val platformTheme = platformTheme()

    MaterialTheme(
        colors = platformTheme.materialColors(),
    ) {
        Surface(
            color = MaterialTheme.colors.background,
            contentColor = MaterialTheme.colors.onBackground,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
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
                        style = platformTheme.productNameStyle,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.product_line),
                        style = platformTheme.primaryLineStyle,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.brand_promise),
                        style = platformTheme.supportingLineStyle,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(Res.string.application_shell_note),
                        style = platformTheme.buildNoteStyle,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
