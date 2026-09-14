package app.posato.feature.about

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class AboutScreenPreviewDataProvider : PreviewParameterProvider<String?> {
    override val values: Sequence<String?> = sequenceOf("1.0.0", null)
}
