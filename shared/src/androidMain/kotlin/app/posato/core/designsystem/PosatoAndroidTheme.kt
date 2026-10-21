package app.posato.core.designsystem

import androidx.compose.runtime.Composable

/**
 * Posato's theme for Android screens that live outside the shared application, such as the block screen. It follows
 * the system's Dark Mode with the same palette as the application.
 */
@Composable
public fun PosatoAndroidTheme(content: @Composable () -> Unit) {
    PosatoTheme(content = content)
}
