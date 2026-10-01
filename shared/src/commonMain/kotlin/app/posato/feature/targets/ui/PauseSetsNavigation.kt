package app.posato.feature.targets.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.posato.feature.sync.domain.PauseSetId

/** Which set the Pause sets destination shows, and each set's own website drafts and list positions. */
@Stable
internal class PauseSetsNavigation {
    var openSet: PauseSetId? by mutableStateOf(null)
    private val browsers = mutableMapOf<PauseSetId, TargetsBrowserState>()

    fun browserFor(setId: PauseSetId): TargetsBrowserState {
        return browsers.getOrPut(setId) { TargetsBrowserState() }
    }

    fun open(
        setId: PauseSetId,
        category: TargetsCategory? = null,
    ) {
        val browser = browserFor(setId)
        if (category != null) {
            browser.category = category
            if (category == TargetsCategory.WEBSITES) {
                browser.searching = false
                browser.showingWebsiteEditor = false
            }
        }
        openSet = setId
    }
}
