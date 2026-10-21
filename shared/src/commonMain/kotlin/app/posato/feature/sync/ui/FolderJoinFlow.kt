package app.posato.feature.sync.ui

import app.posato.feature.sync.folder.FolderContents
import app.posato.feature.sync.folder.PairingAcceptResult
import kotlinx.coroutines.delay
import kotlin.math.min

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

    data object Reading : CodeStep

    data object RemovedCopy : CodeStep

    data class Enter(
        val error: PairingAcceptResult?,
    ) : CodeStep

    data class Ready(
        val source: CodeSource,
    ) : CodeStep
}

/** The two steps of joining a folder workspace, which a person may do in either order. */
internal data class FolderJoinSteps(
    val folderChosen: Boolean,
    val code: CodeStep,
) {
    /** Both steps are done, so the device joins without another press. */
    val joinsNow: Boolean
        get() {
            return folderChosen && code is CodeStep.Ready
        }
}

internal fun folderJoinSteps(
    folder: String?,
    contents: FolderContents,
    code: PendingCode?,
    error: PairingAcceptResult?,
): FolderJoinSteps {
    val step = when {
        code != null -> CodeStep.Ready(code.source)

        folder == null -> CodeStep.NotYet

        // A failed code keeps the field even in a folder that reads as empty, so the error stays where it was typed.
        contents == FolderContents.EMPTY && error == null -> CodeStep.Create

        else -> CodeStep.Enter(error)
    }
    return FolderJoinSteps(folderChosen = folder != null, code = step)
}

/** Accepts a code, and while its offer has not reached this copy of the folder, tries again later. */
internal class FolderJoiner(
    private val accept: suspend (String) -> PairingAcceptResult,
    private val now: () -> Long,
) {
    /**
     * Returns the first result that is not a wait: joined, or a code that cannot work. An offer that is not in the
     * folder yet, or a folder that cannot be read yet, is tried again with a growing delay for up to ten minutes,
     * the life of a pairing code; [onWaiting] tells the section while it waits.
     */
    suspend fun join(
        code: String,
        onWaiting: (Boolean) -> Unit = {},
    ): PairingAcceptResult {
        val started = now()
        var attempt = 0
        while (true) {
            val result = accept(code)
            val waits = result == PairingAcceptResult.NOT_FOUND || result == PairingAcceptResult.UNAVAILABLE
            if (!waits || now() - started >= GIVE_UP_MILLIS) {
                onWaiting(false)
                return result
            }
            onWaiting(true)
            delay(retryDelayMillis(attempt))
            attempt += 1
        }
    }
}

internal fun retryDelayMillis(attempt: Int): Long {
    return min(FIRST_RETRY_MILLIS shl attempt.coerceAtMost(MAX_DOUBLINGS), LONGEST_RETRY_MILLIS)
}

private const val FIRST_RETRY_MILLIS = 2_000L
private const val LONGEST_RETRY_MILLIS = 30_000L
private const val MAX_DOUBLINGS = 4
private const val GIVE_UP_MILLIS = 10 * 60_000L
