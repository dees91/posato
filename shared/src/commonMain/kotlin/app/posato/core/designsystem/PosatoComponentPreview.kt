package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun PosatoComponentPreview(content: @Composable ColumnScope.() -> Unit) {
    PosatoTheme {
        Surface {
            Column(
                modifier = Modifier.padding(PosatoSpace.Large),
                verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
                content = content,
            )
        }
    }
}
