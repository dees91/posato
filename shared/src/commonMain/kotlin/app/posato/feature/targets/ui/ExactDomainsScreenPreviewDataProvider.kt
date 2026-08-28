package app.posato.feature.targets.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf

internal class ExactDomainsScreenPreviewDataProvider : PreviewParameterProvider<ExactDomainsScreenPreviewDataProvider.ExactDomainsPreviewState> {
    companion object {
        private val previewDomains = persistentListOf(
            "example.com",
            "news.example",
        )

        private val loading = ExactDomainsPreviewState(
            name = "Loading",
            state = ExactDomainsUiState(),
        )

        private val unableToLoad = ExactDomainsPreviewState(
            name = "Unable to load",
            state = ExactDomainsUiState(
                operationFailure = ExactDomainsOperationFailure.LOAD_FAILED,
                isLoading = false,
            ),
        )

        private val empty = ExactDomainsPreviewState(
            name = "Empty",
            state = ExactDomainsUiState(
                isLoading = false,
                hasLoaded = true,
            ),
        )

        private val domains = ExactDomainsPreviewState(
            name = "Domains",
            state = ExactDomainsUiState(
                domains = previewDomains,
                isLoading = false,
                hasLoaded = true,
            ),
        )

        private val editing = ExactDomainsPreviewState(
            name = "Editing",
            state = ExactDomainsUiState(
                domains = previewDomains,
                editingDomain = "example.com",
                isLoading = false,
                hasLoaded = true,
            ),
        )

        private val validationError = ExactDomainsPreviewState(
            name = "Validation error",
            state = ExactDomainsUiState(
                inputFailure = ExactDomainEntryFailure.EMPTY,
                isLoading = false,
                hasLoaded = true,
            ),
        )

        private val saving = ExactDomainsPreviewState(
            name = "Saving",
            state = ExactDomainsUiState(
                domains = previewDomains,
                editingDomain = "example.com",
                isLoading = false,
                isSaving = true,
                hasLoaded = true,
            ),
        )

        private val retryableChangeFailure = ExactDomainsPreviewState(
            name = "Retryable change failure",
            state = ExactDomainsUiState(
                domains = previewDomains,
                operationFailure = ExactDomainsOperationFailure.CORRUPTED_POLICY,
                isLoading = false,
                hasLoaded = true,
            ),
        )

        private val revisionConflict = ExactDomainsPreviewState(
            name = "Revision conflict",
            state = ExactDomainsUiState(
                domains = previewDomains,
                operationFailure = ExactDomainsOperationFailure.REVISION_CONFLICT,
                isLoading = false,
                hasLoaded = true,
            ),
        )

        private val saveFailure = ExactDomainsPreviewState(
            name = "Save failure",
            state = ExactDomainsUiState(
                domains = previewDomains,
                operationFailure = ExactDomainsOperationFailure.SAVE_FAILED,
                isLoading = false,
                hasLoaded = true,
            ),
        )
    }

    override val values: Sequence<ExactDomainsPreviewState> = sequenceOf(
        loading,
        unableToLoad,
        empty,
        domains,
        editing,
        validationError,
        saving,
        retryableChangeFailure,
        revisionConflict,
        saveFailure,
    )

    internal data class ExactDomainsPreviewState(
        val name: String,
        val state: ExactDomainsUiState,
    ) {
        override fun toString(): String {
            return name
        }
    }
}
