package com.danimanx.replicantsynth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF04060A)
val InkDeep = Color(0xFF020305)
val Panel = Color(0xFF0A1119)
val PanelHi = Color(0xFF101C28)
val PanelLine = Color(0xFF1B2B3A)
val Cyan = Color(0xFF2FE6D0)
val CyanDim = Color(0xFF14796F)
val Magenta = Color(0xFFFF2E88)
val MagentaDim = Color(0xFF7A1742)
val Amber = Color(0xFFFFB020)
val Lime = Color(0xFFB8FF3D)
val Red = Color(0xFFFF4655)
val TextBright = Color(0xFFE9F7FF)
val TextMid = Color(0xFFA7BFCC)
val TextDim = Color(0xFF5E7787)

val Mono = FontFamily.Monospace

private val scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Ink,
    secondary = Magenta,
    onSecondary = Ink,
    tertiary = Amber,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextBright,
    surface = Panel,
    onSurface = TextBright,
    surfaceVariant = PanelHi,
    onSurfaceVariant = TextMid,
    outline = PanelLine,
    error = Red,
)

private val typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 2.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontSize = 9.sp,
        letterSpacing = 0.4.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
    ),
)

@Composable
fun ReplicantTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
