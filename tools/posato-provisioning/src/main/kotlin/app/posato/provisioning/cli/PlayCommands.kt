package app.posato.provisioning.cli

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.play.PLAY_PACKAGE
import app.posato.provisioning.play.PlayInputs
import app.posato.provisioning.play.PlayListingUpdate
import app.posato.provisioning.play.PlayPromotion
import app.posato.provisioning.play.PlayStatusReport
import app.posato.provisioning.play.PlayTrackName
import app.posato.provisioning.play.PlayUpload
import app.posato.provisioning.play.ReleaseStatus
import app.posato.provisioning.play.UploadRequest
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

private val TRACKS = PlayTrackName.entries.map { it.option }.toTypedArray()
private val STATUSES = ReleaseStatus.entries.map { it.option }.toTypedArray()

class PlayCommand : CliktCommand(name = "play") {
    override fun help(context: Context): String =
        "Inspect, upload, promote, and list the Android app $PLAY_PACKAGE on Google Play through the Play Developer API. " +
            "The API acts only on an app whose first build was uploaded by hand in Play Console."

    override fun run() = Unit
}

class PlayStatusCommand :
    ProvisioningCommand(
        "status",
        "Reports every track with its releases (version codes, status, rollout fraction, release-note languages) and the " +
            "store listing locales. Read-only: the edit it opens is deleted, never committed.",
    ) {
    override fun execute(session: Session): JsonElement = PlayStatusReport(session.playClient()).report()
}

/**
 * Paths are plain strings checked inside the command, so a failure goes through redaction instead of printing a
 * personal path in a usage error. Every input is read and checked before the service account signs anything.
 */
class PlayUploadCommand :
    ProvisioningCommand(
        "upload",
        "Uploads an Android App Bundle and its R8 mapping.txt in one edit, makes the bundle the release of --track with " +
            "the release notes from --notes-dir, and commits. Prints the version code Play read from the bundle.",
    ) {
    private val aab by option("--aab", help = "The signed .aab file.").required()
    private val mapping by option("--mapping", help = "The R8 mapping.txt of that build.").required()
    private val track by option("--track", help = "The track the release goes to.").choice(*TRACKS).required()
    private val notesDir by option(
        "--notes-dir",
        help = "A directory of <locale>.txt release notes such as en-US.txt and pl-PL.txt, each at most 500 characters.",
    )
    private val status by option(
        "--status",
        help = "completed rolls the release out on the track; draft stages it (required while the app is still a draft).",
    ).choice(*STATUSES).default(ReleaseStatus.COMPLETED.option)

    override fun execute(session: Session): JsonElement {
        val request = UploadRequest(
            bundle = PlayInputs.bundle(Path.of(aab)),
            mapping = PlayInputs.mapping(Path.of(mapping)),
            track = PlayTrackName.of(track),
            status = ReleaseStatus.of(status),
            notes = notesDir?.let { directory -> PlayInputs.releaseNotes(Path.of(directory)) }.orEmpty(),
        )
        return PlayUpload(session.playClient()).upload(request)
    }
}

class PlayPromoteCommand :
    ProvisioningCommand(
        "promote",
        "Makes the release holding --version-code on --from the release of --to, with the same name and release notes, " +
            "in one committed edit. Nothing is uploaded again.",
    ) {
    private val versionCode by option("--version-code", help = "The version code to promote, such as 1060101.")
        .long()
        .restrictTo(min = 1)
        .required()
    private val from by option("--from", help = "The track that holds the release.").choice(*TRACKS).required()
    private val to by option("--to", help = "The track that receives it.").choice(*TRACKS).required()
    private val status by option("--status", help = "completed rolls it out on --to; draft stages it.")
        .choice(*STATUSES)
        .default(ReleaseStatus.COMPLETED.option)

    override fun execute(session: Session): JsonElement {
        if (from == to) {
            throw ProvisioningException(ErrorCode.RELEASE_INPUT_INVALID, "--from and --to name the same track.", "Name two different tracks.")
        }
        return PlayPromotion(session.playClient()).promote(versionCode, PlayTrackName.of(from), PlayTrackName.of(to), ReleaseStatus.of(status))
    }
}

class PlayListingCommand :
    ProvisioningCommand(
        "listing",
        "Replaces the store listing of every locale directory in --dir (Play codes such as en-US, pl-PL, pt-BR): title.txt " +
            "(30 characters), short-description.txt (80), full-description.txt (4000), and, when present, icon.png " +
            "(512 x 512), feature-graphic.png (1024 x 500, no alpha), and phone/*.png (2 to 8, in file-name order). All " +
            "inputs are checked before anything is sent, and every change lands in one committed edit.",
    ) {
    private val dir by option("--dir", help = "The directory holding one subdirectory per locale.").required()

    override fun execute(session: Session): JsonElement {
        val listings = PlayInputs.listings(Path.of(dir))
        return PlayListingUpdate(session.playClient()).update(listings)
    }
}
