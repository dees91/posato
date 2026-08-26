package app.posato

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.posato.di.ApplicationGraph
import kotlin.test.Test

class ApplicationShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun application_shell_renders_the_accepted_content() {
        val applicationGraph: ApplicationGraph = FakeApplicationGraph()

        runComposeUiTest {
            setContent {
                applicationGraph.application.Content()
            }

            onNodeWithText("Posato").assertExists()
            onNodeWithText("Pause. Then choose.").assertExists()
            onNodeWithText("A quiet pause between impulse and action.").assertExists()
            onNodeWithText("This build contains the application shell only.").assertExists()
        }
    }
}

private class FakeApplicationGraph : ApplicationGraph {
    override val application: PosatoApplication = PosatoApplication()
}
