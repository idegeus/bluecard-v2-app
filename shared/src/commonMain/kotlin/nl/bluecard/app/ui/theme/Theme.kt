package nl.bluecard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1558B0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFC75B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E6EF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF00458E),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6C3A00),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF111318),
    surface = Color(0xFF1A1C21),
    surfaceVariant = Color(0xFF2C3038),
)

/** Colours of the card table, independent of light/dark mode so cards always look like cards. */
/** Colours of the card table. The felt and card-back colours follow the chosen [Skins]. */
object TableColors {
    val Felt: Color get() = Skins.table.felt
    val FeltDark: Color get() = Skins.table.feltDark
    val FeltLight: Color get() = Skins.table.feltLight
    val OnFelt = Color(0xFFF1F8F4)
    val OnFeltMuted: Color get() = Skins.table.onFeltMuted

    /** A soft panel on the table. */
    val Shade = Color.Black.copy(alpha = 0.22f)

    /** For thin lines and translucent fills on the table. */
    val Ink = Color.White
    val Highlight = Color(0xFFFFD54F)
    val TurnGlow = Color(0xFFFFC107)
    val CardFace = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFB0B7C3)
    val CardRed = Color(0xFFD32F2F)
    val CardBlack = Color(0xFF1F2328)
    val CardBack: Color get() = Skins.cardBack.base
    val CardBackDark: Color get() = Skins.cardBack.dark
    val Selected = Color(0xFF2979FF)
    val Danger = Color(0xFFE53935)
}

@Composable
fun BlueCardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
