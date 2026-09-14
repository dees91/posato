package app.posato.feature.licenses

internal enum class LicenseDocument(
    val title: String,
    val resourcePath: String
) {
    LICENSE("License", "files/legal/LICENSE"),
    NOTICE("Notice", "files/legal/NOTICE"),
    THIRD_PARTY_NOTICES("Third-party notices", "files/legal/THIRD_PARTY_NOTICES.md"),
}

internal data class LicensesUiState(
    val document: LicenseDocument? = null,
    val text: String? = null,
    val loadFailed: Boolean = false,
)
