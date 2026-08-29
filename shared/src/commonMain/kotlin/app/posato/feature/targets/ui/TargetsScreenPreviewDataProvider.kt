package app.posato.feature.targets.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf

internal class TargetsScreenPreviewDataProvider : PreviewParameterProvider<TargetsScreenPreviewDataProvider.TargetsPreviewState> {
    private val domains = persistentListOf("example.com", "news.example")

    override val values: Sequence<TargetsPreviewState> = sequenceOf(
        TargetsPreviewState("Loading", TargetsUiState()),
        TargetsPreviewState(
            "Unable to load",
            TargetsUiState(operationFailure = TargetsOperationFailure.LOAD_FAILED, isLoading = false),
        ),
        TargetsPreviewState("Empty", TargetsUiState(isLoading = false, hasLoaded = true)),
        TargetsPreviewState(
            "Application mapping required",
            TargetsUiState(
                domains = domains,
                applicationPolicyName = "Social feeds",
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Editing application group",
            TargetsUiState(
                domains = domains,
                applicationPolicyName = "Social feeds",
                isEditingApplicationPolicy = true,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Application validation",
            TargetsUiState(
                applicationPolicyInputFailure = ApplicationPolicyEntryFailure.EMPTY,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Editing website",
            TargetsUiState(
                domains = domains,
                editingDomain = "example.com",
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Website validation",
            TargetsUiState(
                domains = domains,
                domainInputFailure = ExactDomainEntryFailure.EMPTY,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Saving application group",
            TargetsUiState(
                domains = domains,
                applicationPolicyName = "Social feeds",
                isEditingApplicationPolicy = true,
                savingMutation = TargetMutation.APPLICATION_POLICY,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Saving website",
            TargetsUiState(
                domains = domains,
                editingDomain = "example.com",
                savingMutation = TargetMutation.DOMAIN,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Reloading",
            TargetsUiState(
                domains = domains,
                applicationPolicyName = "Social feeds",
                isLoading = true,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Retryable corruption",
            TargetsUiState(
                domains = domains,
                applicationPolicyName = "Social feeds",
                operationFailure = TargetsOperationFailure.CORRUPTED_POLICY,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Revision conflict",
            TargetsUiState(
                domains = domains,
                applicationPolicyName = "Social feeds",
                operationFailure = TargetsOperationFailure.REVISION_CONFLICT,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
        TargetsPreviewState(
            "Save failure",
            TargetsUiState(
                domains = domains,
                applicationPolicyName = "Social feeds",
                operationFailure = TargetsOperationFailure.SAVE_FAILED,
                isLoading = false,
                hasLoaded = true,
            ),
        ),
    )

    internal data class TargetsPreviewState(
        val name: String,
        val state: TargetsUiState,
    ) {
        override fun toString(): String {
            return name
        }
    }
}
