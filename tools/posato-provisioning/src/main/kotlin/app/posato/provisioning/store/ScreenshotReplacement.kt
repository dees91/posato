package app.posato.provisioning.store

import app.posato.provisioning.asc.ScreenshotClient
import app.posato.provisioning.asc.ScreenshotUploader
import app.posato.provisioning.asc.Sleeper
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.model.ScreenshotResource
import java.time.Clock
import java.time.Duration

private const val COMPLETE = "COMPLETE"
private const val FAILED = "FAILED"
private val DELIVERY_DEADLINE: Duration = Duration.ofMinutes(10)
private val POLL_INTERVAL: Duration = Duration.ofSeconds(5)

enum class SetOutcome { UNCHANGED, REPLACED }

data class SetReplacement(
    val setId: String,
    val outcome: SetOutcome,
)

/**
 * Replaces one screenshot set with local files, or leaves it alone when it already holds exactly those files.
 *
 * "Exactly" means the same file names in the same order, each with the same MD5 App Store Connect recorded at
 * commit, and each delivered. Anything short of that is replaced whole: every existing screenshot is deleted first,
 * because a set holds at most ten and a partial replacement would mix old and new images in an order no one chose.
 */
class ScreenshotReplacement(
    private val client: ScreenshotClient,
    private val uploader: ScreenshotUploader,
    private val clock: Clock,
    private val sleeper: Sleeper,
) {
    fun replace(
        localizationId: String,
        slot: ScreenshotSlot,
        files: List<ScreenshotFile>,
    ): SetReplacement {
        val setId = client.sets(localizationId).firstOrNull { set -> set.attributes.screenshotDisplayType == slot.displayType }?.id
            ?: client.createSet(localizationId, slot.displayType).id
        val existing = client.screenshots(setId)
        if (matches(existing, files)) return SetReplacement(setId, SetOutcome.UNCHANGED)
        existing.forEach { screenshot -> client.delete(screenshot.id) }
        files.forEach { file ->
            val reserved = client.reserve(setId, file.fileName, file.bytes.size)
            uploader.upload(reserved.attributes.uploadOperations.orEmpty(), file.bytes)
            client.commit(reserved.id, file.checksum)
        }
        return SetReplacement(setId, SetOutcome.REPLACED)
    }

    /** Waits, bounded, until App Store Connect has processed every screenshot in the named sets. */
    fun awaitDelivery(setIds: List<String>) {
        val deadline = clock.instant().plus(DELIVERY_DEADLINE)
        while (true) {
            val screenshots = setIds.flatMap { setId -> client.screenshots(setId) }
            val failed = screenshots.filter { screenshot -> screenshot.deliveryState == FAILED }
            if (failed.isNotEmpty()) throw rejected(failed)
            if (screenshots.all { screenshot -> screenshot.deliveryState == COMPLETE }) return
            if (!clock.instant().isBefore(deadline)) throw timedOut()
            sleeper.sleep(POLL_INTERVAL)
        }
    }

    private fun matches(
        existing: List<ScreenshotResource>,
        files: List<ScreenshotFile>,
    ): Boolean = existing.size == files.size &&
        existing.zip(files).all { (screenshot, file) ->
            screenshot.attributes.fileName == file.fileName &&
                screenshot.attributes.sourceFileChecksum == file.checksum &&
                screenshot.deliveryState == COMPLETE
        }

    private fun rejected(failed: List<ScreenshotResource>): ProvisioningException {
        val codes = failed.flatMap { screenshot -> screenshot.attributes.assetDeliveryState?.errors.orEmpty() }.mapNotNull { it.code }
        val suffix = if (codes.isEmpty()) "" else " Reported: ${codes.distinct().joinToString(", ")}."
        return ProvisioningException(
            ErrorCode.SCREENSHOTS_NOT_DELIVERED,
            "App Store Connect could not process ${failed.size} screenshot(s).$suffix",
            "Check the image size and format against the screenshot specifications, then rerun `store prepare`.",
        )
    }

    private fun timedOut(): ProvisioningException = ProvisioningException(
        ErrorCode.SCREENSHOTS_NOT_DELIVERED,
        "App Store Connect did not finish processing the screenshots within ${DELIVERY_DEADLINE.toMinutes()} minutes.",
        "Run `store status --version <version>` later; rerun `store prepare` only if a screenshot is not COMPLETE.",
    )
}
