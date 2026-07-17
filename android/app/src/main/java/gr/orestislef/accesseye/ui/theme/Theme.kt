//
//  Theme.kt
//  AccessEye
//
//  Material 3 theme for the whole app. AccessEye is dark-first by design: the
//  camera screen is a full-bleed viewfinder and every other screen sits on
//  black for maximum contrast (low-vision users, README §7). The accent is the
//  blue family from the iOS accent color / app icon (#2E6BE6), brightened where
//  it must read against black. Base type sizes are bumped slightly above the
//  Material defaults so text is comfortable without forcing users into system
//  font scaling.
//

package gr.orestislef.accesseye.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The iOS accent / icon blue and companions picked for contrast on black.
val AccessBlue = Color(0xFF2E6BE6)        // brand blue (icon / iOS accent)
val AccessBlueBright = Color(0xFF8AB0FF)  // readable on black (primary in dark)
val AccessBlueDeep = Color(0xFF00306E)    // text on the bright blue

private val DarkColors = darkColorScheme(
    primary = AccessBlueBright,
    onPrimary = AccessBlueDeep,
    primaryContainer = AccessBlue,
    onPrimaryContainer = Color.White,

    secondary = Color(0xFFAEC6FF),
    onSecondary = Color(0xFF0A2C6B),
    secondaryContainer = Color(0xFF234C9E),
    onSecondaryContainer = Color(0xFFDCE5FF),

    tertiary = Color(0xFF9CD6FF),
    onTertiary = Color(0xFF00344C),

    // Pure black background: matches the viewfinder and gives OLED contrast.
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0E1116),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C2230),
    onSurfaceVariant = Color(0xFFC8CEDC),
    outline = Color(0xFF8E94A3),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Material defaults, nudged larger for low-vision readability. Sizes still
// scale with the system font setting (they are sp).
private val Base = Typography()
private val AccessTypography = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = Base.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = Base.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
)

/** App-wide theme wrapper. Always dark — see file header. */
@Composable
fun AccessEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AccessTypography,
        content = content,
    )
}
