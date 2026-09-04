package app.posato

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.session.data.LocalSessionStore
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionDestinationSwitch
import app.posato.feature.session.ui.SessionScreen
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsScreen
import dev.zacsweers.metro.Inject

@Inject
class PosatoApplication internal constructor(
    private val store: LocalTargetPolicyStore,
    private val applicationMappings: LocalApplicationMappings,
    private val sessionStore: LocalSessionStore,
    private val sessionIds: SessionIdGenerator,
    private val clock: SessionClock,
    private val timeFormat: SessionTimeFormat,
) {
    @Composable
    fun Content(modifier: Modifier = Modifier) {
        PosatoTheme {
            var showingSession by remember { mutableStateOf(true) }
            Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SessionDestinationSwitch(
                            onOpenSession = { showingSession = true },
                            onOpenPausedItems = { showingSession = false },
                            showingSession = showingSession,
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 24.dp),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (showingSession) {
                            SessionScreen(sessionStore, store, applicationMappings, sessionIds, clock, timeFormat, { showingSession = false })
                        } else {
                            TargetsScreen(store, applicationMappings)
                        }
                    }
                }
            }
        }
    }
}
