package app.posato.feature.targets.ui

import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainInputFailure
import app.posato.feature.targets.domain.ExactDomainInputResult

internal sealed interface ExactDomainSubmission {
    class Ready(
        val canonicalDomains: List<String>
    ) : ExactDomainSubmission {
        override fun toString(): String {
            return "ExactDomainSubmission.Ready(redacted)"
        }
    }

    data class EntryFailed(
        val failure: ExactDomainEntryFailure
    ) : ExactDomainSubmission

    data class OperationFailed(
        val failure: TargetsOperationFailure
    ) : ExactDomainSubmission
}

internal fun createExactDomainSubmission(
    state: TargetsUiState,
    input: String
): ExactDomainSubmission {
    val domainResult = ExactDomain.parse(input)
    if (domainResult is ExactDomainInputResult.Failure) {
        return ExactDomainSubmission.EntryFailed(domainResult.reason.toEntryFailure())
    }
    val canonicalValue = (domainResult as ExactDomainInputResult.Success).domain.canonicalValue
    val isDuplicate = wwwCoveredBy(
        canonicalValue,
        state.domains.filter { existingDomain -> existingDomain != state.editingDomain },
    ) != null

    return when {
        isDuplicate -> ExactDomainSubmission.EntryFailed(ExactDomainEntryFailure.DUPLICATE)

        state.editingDomain == null -> ExactDomainSubmission.Ready(state.domains + canonicalValue)

        state.editingDomain !in state.domains -> ExactDomainSubmission.OperationFailed(TargetsOperationFailure.REVISION_CONFLICT)

        else -> ExactDomainSubmission.Ready(
            state.domains.map { existingDomain ->
                if (existingDomain == state.editingDomain) canonicalValue else existingDomain
            },
        )
    }
}

internal fun wwwCoveredBy(
    candidate: String,
    listed: Iterable<String>
): String? {
    val parsed = ExactDomain.restore(candidate) ?: return listed.firstOrNull { existing -> existing == candidate }
    return listed.firstOrNull { existing ->
        ExactDomain.restore(existing)?.covers(parsed) == true
    }
}

private fun ExactDomainInputFailure.toEntryFailure(): ExactDomainEntryFailure {
    return when (this) {
        ExactDomainInputFailure.EMPTY -> ExactDomainEntryFailure.EMPTY
        ExactDomainInputFailure.TOO_LONG -> ExactDomainEntryFailure.TOO_LONG
        ExactDomainInputFailure.INVALID_DOMAIN -> ExactDomainEntryFailure.INVALID_DOMAIN
    }
}
