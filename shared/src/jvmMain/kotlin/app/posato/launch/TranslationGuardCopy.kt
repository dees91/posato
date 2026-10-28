package app.posato.launch

import app.posato.generated.resources.Res
import app.posato.generated.resources.translation_guard_download
import app.posato.generated.resources.translation_guard_message
import app.posato.generated.resources.translation_guard_quit
import app.posato.generated.resources.translation_guard_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** Copy for the Rosetta refusal dialog, in the language the desktop process is using. */
public data class TranslationGuardCopy(
    val title: String,
    val message: String,
    val download: String,
    val quit: String,
)

/** Loads the Rosetta refusal dialog after the desktop process has chosen its locale. */
public fun translationGuardCopy(): TranslationGuardCopy = runBlocking {
    TranslationGuardCopy(
        title = getString(Res.string.translation_guard_title),
        message = getString(Res.string.translation_guard_message),
        download = getString(Res.string.translation_guard_download),
        quit = getString(Res.string.translation_guard_quit),
    )
}
