package app.posato.prototype.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoBody
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoEyebrow
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoIntervalArtwork
import app.posato.prototype.designsystem.PosatoPanel
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoWordmark

@Composable
internal fun FoundationSamples(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious)) {
        PosatoSection(titleContent = { Text("Open interval") }) {
            PosatoActionRow {
                PosatoWordmark()
                PosatoIntervalArtwork()
            }
            PosatoCaption("One-color mark and two-tone artwork. Decorative graphics carry no extra accessibility label.")
        }
        PaletteSamples()
        PosatoSection(titleContent = { Text("A quiet type hierarchy") }) {
            PosatoEyebrow("A LITTLE SPACE FOR INTENTION")
            Text("Pause. Then choose.", style = MaterialTheme.typography.headlineMedium)
            Text("Session active until 18:30", style = MaterialTheme.typography.titleMedium)
            PosatoBody("Clear language, comfortable rhythm, and room for text to reflow.")
            PosatoCaption("Supporting information stays secondary, not invisible.")
        }
        IconSamples()
        PosatoSection(titleContent = { Text("Spacing and surfaces") }) {
            listOf(
                PosatoSpace.Tiny,
                PosatoSpace.Small,
                PosatoSpace.Medium,
                PosatoSpace.Large,
                PosatoSpace.Section,
                PosatoSpace.Spacious,
            ).forEach { space ->
                Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                    PosatoCaption(space.toString(), Modifier.width(PosatoSize.Input))
                    Box(Modifier.size(space, PosatoSpace.Large).background(MaterialTheme.colorScheme.primary))
                }
            }
            PosatoPanel { PosatoBody("Subtle border, warm surface, and no decorative elevation.") }
        }
    }
}

@Composable
private fun PaletteSamples(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    PosatoSection(modifier = modifier, titleContent = { Text("Semantic palette") }) {
        PosatoActionRow {
            ColorSample("Moss", palette.primary, palette.onPrimary)
            ColorSample("Paper", palette.surface, palette.onSurface)
            ColorSample("Mist", palette.primaryContainer, palette.onPrimaryContainer)
            ColorSample("Canvas", palette.background, palette.onBackground)
            ColorSample("Caution", palette.tertiaryContainer, palette.onTertiaryContainer)
            ColorSample("Critical", palette.errorContainer, palette.onErrorContainer)
        }
    }
}

@Composable
private fun ColorSample(
    label: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(PosatoSize.Input).background(background, MaterialTheme.shapes.medium).padding(PosatoSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
    ) {
        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
        Text(
            "#" + background.toArgb().toUInt().toString(HEX_RADIX).takeLast(HEX_COLOR_LENGTH).uppercase(),
            color = foreground,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun IconSamples(modifier: Modifier = Modifier) {
    PosatoSection(modifier = modifier, titleContent = { Text("Line icons") }) {
        PosatoActionRow {
            listOf(
                PosatoIcons.Pause to "Pause",
                PosatoIcons.Apps to "Apps",
                PosatoIcons.Globe to "Website",
                PosatoIcons.Cloud to "iCloud",
                PosatoIcons.Clock to "Time",
                PosatoIcons.Check to "Check",
                PosatoIcons.Mac to "Mac",
                PosatoIcons.Phone to "iPhone",
                PosatoIcons.Arrow to "Continue",
            ).forEach { (icon, label) ->
                Column(Modifier.padding(PosatoSpace.Small), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                    PosatoIcon(icon, contentDescription = null)
                    PosatoCaption(label)
                }
            }
        }
    }
}

private const val HEX_COLOR_LENGTH = 6
private const val HEX_RADIX = 16
