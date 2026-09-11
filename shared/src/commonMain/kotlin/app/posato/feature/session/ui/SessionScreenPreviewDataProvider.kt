package app.posato.feature.session.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.posato.feature.enforcement.EnforcedSet
import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.onboarding.MacHelperReadiness
import app.posato.feature.onboarding.MacSetupActivity
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.session.domain.LocalSessionStatus.Active
import app.posato.feature.session.domain.LocalSessionStatus.Ended
import app.posato.feature.session.domain.LocalSessionStatus.Inactive
import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.domain.SessionReview
import app.posato.feature.session.domain.SessionSetupFailure
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncIdentifier
import kotlinx.collections.immutable.persistentListOf

internal class SessionScreenPreviewDataProvider : PreviewParameterProvider<SessionScreenPreviewDataProvider.SessionPreviewState> {
    private val record = SessionRecord(previewSessionId(9), START, END)

    override val values: Sequence<SessionPreviewState> = sequenceOf(
        SessionPreviewState("Loading", SessionUiState()),
        SessionPreviewState(
            "Unable to load",
            SessionUiState(operationFailure = SessionOperationFailure.LOAD_FAILED),
        ),
        SessionPreviewState(
            "Inactive",
            SessionUiState(
                status = Inactive,
                review = SessionReview(domains = persistentListOf("example.com", "news.example")),
            ),
        ),
        SessionPreviewState(
            "Inactive Mac setup unchecked",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(),
        ),
        SessionPreviewState(
            "Inactive Mac setup checking",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(activity = MacSetupActivity.CHECKING),
        ),
        SessionPreviewState(
            "Inactive Mac setup ready",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(readiness = MacHelperReadiness.READY),
        ),
        SessionPreviewState(
            "Inactive Mac setup approval required",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(readiness = MacHelperReadiness.APPROVAL_REQUIRED),
        ),
        SessionPreviewState(
            "Inactive Mac setup uncertain",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(readiness = MacHelperReadiness.UNCERTAIN),
        ),
        SessionPreviewState(
            "Inactive Mac setup recovery required",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(readiness = MacHelperReadiness.RECOVERY_REQUIRED),
        ),
        SessionPreviewState(
            "Inactive Mac setup not enabled",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(readiness = MacHelperReadiness.NOT_ENABLED),
        ),
        SessionPreviewState(
            "Inactive Mac setup unavailable and repeated",
            inactiveWithItems(),
            macSetup = MacSetupPresentation(readiness = MacHelperReadiness.UNAVAILABLE, repeatedResult = true),
        ),
        SessionPreviewState(
            "Setup",
            SessionUiState(
                status = Inactive,
                isSettingUp = true,
                formattedPreviewEnd = "14:30",
            ),
        ),
        SessionPreviewState(
            "Setup invalid",
            SessionUiState(
                status = Inactive,
                isSettingUp = true,
                durationMinutes = 3,
                setupFailure = SessionSetupFailure.TOO_SHORT,
                formattedPreviewEnd = "14:03",
            ),
        ),
        SessionPreviewState(
            "Review ready",
            SessionUiState(
                status = Inactive,
                isSettingUp = true,
                isReviewing = true,
                isReviewReady = true,
                review = SessionReview(
                    domains = persistentListOf("example.com", "news.example"),
                    applicationGroupName = "Social feeds",
                    selectedMappingCount = 2,
                ),
                formattedReviewEnd = "15:00",
            ),
        ),
        SessionPreviewState(
            "Review blocked",
            SessionUiState(
                status = Inactive,
                isSettingUp = true,
                isReviewing = true,
                isReviewReady = true,
                review = SessionReview(actionRequired = SessionActionRequired.NO_EFFECTIVE_ITEMS),
                formattedReviewEnd = "15:00",
            ),
        ),
        SessionPreviewState(
            "Active",
            SessionUiState(
                status = Active(record, 42 * 60_000L),
                review = SessionReview(
                    domains = persistentListOf("example.com"),
                    applicationGroupName = "Social feeds",
                    selectedMappingCount = 1,
                ),
                remainingMillis = 42 * 60_000L,
                formattedActiveEnd = "15:12",
                enforcement = EnforcementState.Active(false),
                enforced = EnforcedSet(
                    domains = persistentListOf("example.com"),
                    applicationCount = 1,
                ),
            ),
        ),
        SessionPreviewState(
            "Active needs attention",
            SessionUiState(
                status = Active(record, 42 * 60_000L),
                review = SessionReview(domains = persistentListOf("example.com")),
                remainingMillis = 42 * 60_000L,
                formattedActiveEnd = "15:12",
                enforcement = EnforcementState.ActionRequired(EnforcementActionKind.APPLY_FAILED, true),
                enforced = EnforcedSet(domains = persistentListOf("example.com")),
            ),
        ),
        SessionPreviewState(
            "Active resume",
            SessionUiState(
                status = Active(record, 42 * 60_000L),
                review = SessionReview(domains = persistentListOf("example.com")),
                remainingMillis = 42 * 60_000L,
                formattedActiveEnd = "15:12",
                enforcement = EnforcementState.ActionRequired(EnforcementActionKind.RESUME_REQUIRED, true),
                enforced = EnforcedSet(domains = persistentListOf("example.com")),
            ),
        ),
        SessionPreviewState(
            "Confirming early end",
            SessionUiState(
                status = Active(record, 42 * 60_000L),
                confirmingEarlyEnd = true,
                remainingMillis = 42 * 60_000L,
                formattedActiveEnd = "15:12",
            ),
        ),
        SessionPreviewState(
            "Ended expired",
            SessionUiState(
                status = Ended(record, SessionEndKind.EXPIRED),
                formattedActiveEnd = "15:00",
            ),
        ),
        SessionPreviewState(
            "Ended early",
            SessionUiState(
                status = Ended(record, SessionEndKind.ENDED_EARLY),
                formattedActiveEnd = "14:47",
            ),
        ),
    )

    internal class SessionPreviewState(
        val name: String,
        val state: SessionUiState,
        val macSetup: MacSetupPresentation? = null,
    )

    private fun inactiveWithItems(): SessionUiState {
        return SessionUiState(
            status = Inactive,
            review = SessionReview(domains = persistentListOf("example.com", "news.example")),
        )
    }

    private companion object {
        const val START: Long = 1_000_000_000_000L
        const val END: Long = START + 30 * 60_000L

        fun previewSessionId(byte: Int): SessionId {
            val bytes = ByteArray(IDENTIFIER_BYTES)
            bytes[VERSION_INDEX] = VERSION_VALUE
            bytes[VARIANT_INDEX] = VARIANT_VALUE.toByte()
            bytes[SUFFIX_INDEX] = byte.toByte()

            return SessionId(checkNotNull(SyncIdentifier.fromUuidV4Bytes(bytes)))
        }

        private const val IDENTIFIER_BYTES: Int = 16
        private const val VERSION_INDEX: Int = 6
        private const val VARIANT_INDEX: Int = 8
        private const val SUFFIX_INDEX: Int = 15
        private const val VERSION_VALUE: Byte = 0x40
        private const val VARIANT_VALUE: Int = 0x80
    }
}
