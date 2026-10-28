package app.posato.feature.targets.ui

import app.posato.generated.resources.Res
import app.posato.generated.resources.pause_sets_copy_suffix
import app.posato.generated.resources.pause_sets_unnamed
import org.jetbrains.compose.resources.getString

/**
 * The words set names are built from, in the app's language. View models build names outside composition, so each
 * entry point loads them once its language is settled; until then, and in tests, they stay English.
 */
internal object SetNameText {
    var unnamed: String = "My set"
        private set
    var copySuffix: String = " (copy)"
        private set

    suspend fun load() {
        unnamed = getString(Res.string.pause_sets_unnamed)
        copySuffix = getString(Res.string.pause_sets_copy_suffix)
    }
}

/** Loads [SetNameText] from a platform entry point that is not a coroutine. */
fun loadSetNameText() {
    kotlinx.coroutines.runBlocking { SetNameText.load() }
}
