package app.posato.feature.sync.ui

import app.posato.feature.sync.folder.FolderContents
import app.posato.feature.sync.folder.PairingAcceptResult

/** Where a pending pairing code came from: a join link the camera opened, or the code field. */
internal enum class CodeSource {
    SCANNED,
    TYPED,
}

internal class PendingCode(
    val code: String,
    val source: CodeSource,
) {
    override fun toString(): String {
        return "PendingCode($source, redacted)"
    }
}

/** The second step of joining: it waits for a folder, creates in an empty one, asks for a code, or has one. */
internal sealed interface CodeStep {
    data object NotYet : CodeStep

    data object Create : CodeStep

    data class Enter(
        val error: PairingAcceptResult?,
    ) : CodeStep

    data class Ready(
        val source: CodeSource,
    ) : CodeStep
}

internal data class FolderJoinSteps(
    val folderChosen: Boolean,
    val code: CodeStep,
) {
    /** Both steps are done, so the device joins without another press. */
    val joinsNow: Boolean
        get() {
            return false
        }
}

internal fun folderJoinSteps(
    folder: String?,
    contents: FolderContents,
    code: PendingCode?,
    error: PairingAcceptResult?,
): FolderJoinSteps {
    return FolderJoinSteps(folderChosen = false, code = CodeStep.NotYet)
}

/** Accepts a code, and while its offer has not reached this copy of the folder, tries again later. */
internal class FolderJoiner(
    private val accept: suspend (String) -> PairingAcceptResult,
    private val now: () -> Long,
) {
    suspend fun join(
        code: String,
        onWaiting: (Boolean) -> Unit = {},
    ): PairingAcceptResult {
        return accept(code)
    }
}
