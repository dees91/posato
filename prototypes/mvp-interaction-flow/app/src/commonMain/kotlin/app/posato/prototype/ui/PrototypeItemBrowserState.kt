package app.posato.prototype.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.posato.prototype.model.PrototypeWebsiteEntry
import kotlinx.coroutines.flow.drop

@Stable
internal class PrototypeItemBrowserState(
    initialSection: PrototypeItemSection,
    initialQuery: String
) {
    var section: PrototypeItemSection by mutableStateOf(initialSection)
    val search: TextFieldState = TextFieldState(initialQuery)
    val websiteDraft: TextFieldState = TextFieldState()
    var searching: Boolean by mutableStateOf(initialQuery.isNotEmpty())
    val websiteScroll: LazyListState = LazyListState()
    val applicationScroll: LazyListState = LazyListState()
    private var appliedSubmission: Int = 0

    fun acceptSubmission(result: PrototypeWebsiteEntry) {
        if (result.revision <= appliedSubmission) return
        appliedSubmission = result.revision
        if (websiteDraft.text.toString() == result.submitted) websiteDraft.setTextAndPlaceCursorAtEnd(result.remainder)
    }
}

@Composable
internal fun rememberPrototypeItemBrowserState(
    initialSection: PrototypeItemSection = PrototypeItemSection.Websites,
    initialQuery: String = ""
): PrototypeItemBrowserState {
    val state = remember { PrototypeItemBrowserState(initialSection, initialQuery) }
    LaunchedEffect(state) {
        snapshotFlow { state.search.text.toString() }.drop(1).collect { state.websiteScroll.requestScrollToItem(0) }
    }
    return state
}
