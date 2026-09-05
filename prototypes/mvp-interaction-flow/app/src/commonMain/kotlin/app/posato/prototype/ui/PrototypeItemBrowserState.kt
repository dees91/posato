package app.posato.prototype.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

@Stable
internal class PrototypeItemBrowserState(
    initialSection: PrototypeItemSection,
    initialQuery: String
) {
    var section: PrototypeItemSection by mutableStateOf(initialSection)
    val search: TextFieldState = TextFieldState(initialQuery)
    val websiteScroll: LazyListState = LazyListState()
    val applicationScroll: LazyListState = LazyListState()
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
