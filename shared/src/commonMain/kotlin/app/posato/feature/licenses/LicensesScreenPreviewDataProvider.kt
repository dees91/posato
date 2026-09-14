package app.posato.feature.licenses

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class LicensesScreenPreviewDataProvider : PreviewParameterProvider<LicensesUiState> {
    override val values: Sequence<LicensesUiState> = sequenceOf(
        LicensesUiState(),
        LicensesUiState(document = LicenseDocument.LICENSE),
        LicensesUiState(document = LicenseDocument.LICENSE, loadFailed = true),
        LicensesUiState(document = LicenseDocument.LICENSE, text = "Example license text for a deterministic preview."),
        LicensesUiState(document = LicenseDocument.NOTICE, text = "Example notice text for a deterministic preview."),
        LicensesUiState(
            document = LicenseDocument.THIRD_PARTY_NOTICES,
            text =
                """
                ## Runtime components

                | Component | Used by | License |
                | --- | --- | --- |
                | Example runtime | macOS, iOS | Apache-2.0 |

                - **Example:** Copyright 2026. See `legal` notices.
                [Apache License, Version 2.0](LICENSE)
                """.trimIndent(),
        ),
    )
}
