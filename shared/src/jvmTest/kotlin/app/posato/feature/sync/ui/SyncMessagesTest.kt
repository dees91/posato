package app.posato.feature.sync.ui

import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncAttentionReason
import app.posato.feature.sync.bootstrap.SyncStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SyncMessagesTest {
    private val states = SyncStatus.entries.flatMap { status ->
        listOf(false, true).flatMap { linked ->
            (SyncAttentionReason.entries + null).flatMap { reason ->
                listOf(false, true).map { removedCopy ->
                    AppleSyncState(status = status, linked = linked, reason = reason, removedWorkspaceCopy = removedCopy)
                }
            }
        }
    }

    /** The English text of each string, read from the shipped resources so a reworded string is checked as shipped. */
    private val english: Map<String, String> = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        .findAll(File("src/commonMain/composeResources/values/strings.xml").readText())
        .associate { match -> match.groupValues[1] to match.groupValues[2] }

    @Test
    fun `given folder mode when any sync state is described then the sentence never names iCloud`() {
        val named = states.filter { state -> "iCloud" in english.getValue(state.statusMessage(folderMode = true).key) }

        assertTrue(named.isEmpty(), "iCloud wording in folder mode for ${named.map { "${it.status} linked=${it.linked} ${it.reason}" }.distinct()}")
    }
}
