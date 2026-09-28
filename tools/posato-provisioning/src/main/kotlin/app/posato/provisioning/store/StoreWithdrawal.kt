package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration

private const val CANCELING_STATE = "CANCELING"
private const val CANCEL_POLLS = 24
private val CANCEL_POLL_INTERVAL: Duration = Duration.ofSeconds(5)

/** The version states a withdrawal can undo: the version sits in a submission App Review holds. */
private val WITHDRAWABLE_VERSION_STATES = setOf("WAITING_FOR_REVIEW", "IN_REVIEW")

/**
 * Withdraws one iOS App Store version from App Review and waits until App Store Connect lets it be edited again.
 *
 * App Store Connect holds one unreleased version per platform, so a version waiting for review blocks every later
 * release until it is withdrawn, renamed, or released. The withdrawal cancels the review submission that holds the
 * version; a submission that also holds anything else (another version, an App Event, a custom product page) is
 * refused before any write, so nothing is withdrawn that was not asked for. The cancellation completes
 * asynchronously, so the command polls, bounded, until the version is editable (normally `DEVELOPER_REJECTED`). A
 * rerun finds either an editable version, which it reports unchanged, or the submission still canceling, which it
 * waits for without writing again.
 */
class StoreWithdrawal(
    private val services: StoreServices
) {
    fun withdraw(version: String): JsonObject {
        val store = services.store
        val app = StoreLookups.app(store)
        val resource = StoreLookups.requireVersion(store, app.id, version)
        val state = resource.state
        if (state in EDITABLE_VERSION_STATES) return result(version, UNCHANGED, state)
        if (state !in WITHDRAWABLE_VERSION_STATES) throw notWithdrawable(version, state)
        val outcome = cancelSubmission(app.id, resource.id, version, state)
        return result(version, outcome, awaitEditable(app.id, version))
    }

    /**
     * Cancels the submission App Review holds the version in, or, when an earlier run already did, only reports that.
     * A submission holding anything else, or none holding the version at all, stops the run before a write.
     */
    private fun cancelSubmission(
        appId: String,
        versionId: String,
        version: String,
        state: String?,
    ): String {
        val holding = StoreLookups.submissions(services.review, appId, SUBMITTED_STATES + CANCELING_STATE)
            .filter { submission -> versionId in submission.versionIds }
        val submitted = holding.firstOrNull { submission -> submission.state in SUBMITTED_STATES }
        if (submitted == null) {
            if (holding.none { submission -> submission.state == CANCELING_STATE }) throw noSubmission(version, state)
            return UNCHANGED
        }
        if (submitted.holdsOtherThan(versionId)) throw holdsOther(version)
        services.review.cancel(submitted.resource.id)
        return "canceled"
    }

    /** Polls the version until the cancellation has returned it to an editable state, or fails once the bound is spent. */
    private fun awaitEditable(
        appId: String,
        version: String,
    ): String? {
        repeat(CANCEL_POLLS) {
            services.pause.sleep(CANCEL_POLL_INTERVAL)
            val state = StoreLookups.requireVersion(services.store, appId, version).state
            if (state in EDITABLE_VERSION_STATES) return state
            // App Review can still approve the version before the cancellation lands; waiting cannot change that.
            if (state !in SUBMITTED_STATES) throw approvedFirst(version, state)
        }
        throw ProvisioningException(
            ErrorCode.WITHDRAWAL_PENDING,
            "App Store Connect accepted the withdrawal of version $version, but the version is not editable after " +
                "${CANCEL_POLLS * CANCEL_POLL_INTERVAL.seconds} seconds.",
            "The cancellation is still being processed. Rerun `store withdraw --version $version` later; a rerun " +
                "sends nothing again and only waits.",
        )
    }

    private fun notWithdrawable(
        version: String,
        state: String?,
    ): ProvisioningException = ProvisioningException(
        ErrorCode.VERSION_NOT_EDITABLE,
        "Version $version is ${state ?: "in an unknown state"}, which a withdrawal from App Review cannot change.",
        "Nothing was changed. Only a version waiting for or in review can be withdrawn; a version pending release or " +
            "released needs a new version number, or a developer removal in App Store Connect.",
    )

    private fun approvedFirst(
        version: String,
        state: String?,
    ): ProvisioningException = ProvisioningException(
        ErrorCode.VERSION_NOT_EDITABLE,
        "The withdrawal of version $version was sent, but the version moved to ${state ?: "an unknown state"} " +
            "instead of becoming editable, so App Review may have finished it first.",
        "Check `store status --version $version`. A version pending release or released needs a new version number " +
            "rather than a rename.",
    )

    private fun holdsOther(version: String): ProvisioningException = ProvisioningException(
        ErrorCode.SUBMISSION_NOT_READY,
        "The review submission holding version $version also holds an item other than this version.",
        "Nothing was changed. Remove the other item from the submission or cancel it in App Store Connect.",
    )

    private fun noSubmission(
        version: String,
        state: String?,
    ): ProvisioningException = ProvisioningException(
        ErrorCode.SUBMISSION_MISSING,
        "Version $version is $state, but no review submission waiting for or in review holds it.",
        "Nothing was changed. Check the version in App Store Connect; `store status --version $version` shows its state.",
    )

    private fun result(
        version: String,
        outcome: String,
        state: String?,
    ): JsonObject = buildJsonObject {
        put("version", version)
        put("submission", outcome)
        put("state", state)
    }
}
