package app.posato.provisioning.play

import app.posato.provisioning.core.ProvisioningException

/**
 * Opens an edit, runs [block], and commits. On any failure, including a failed commit, the edit is deleted, so a
 * half-done change never stays open for a later commit to publish.
 */
fun <T> PlayClient.committing(block: (String) -> T): T {
    val edit = insertEdit()
    var committed = false
    try {
        val result = block(edit)
        commitEdit(edit)
        committed = true
        return result
    } finally {
        if (!committed) discard(edit)
    }
}

/** Opens an edit for reading and always deletes it, so a read can never publish anything. */
fun <T> PlayClient.discarding(block: (String) -> T): T {
    val edit = insertEdit()
    try {
        return block(edit)
    } finally {
        discard(edit)
    }
}

/**
 * Best effort: a delete that fails must not replace the failure that caused it. Google expires an abandoned edit on
 * its own, and the next command opens a new one.
 */
private fun PlayClient.discard(edit: String) {
    try {
        deleteEdit(edit)
    } catch (exception: ProvisioningException) {
        record("The edit could not be deleted (${exception.code}); Google Play expires it on its own.")
    }
}
