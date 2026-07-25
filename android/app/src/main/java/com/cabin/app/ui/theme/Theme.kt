package com.cabin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = ForestContainer,
    onPrimaryContainer = OnForestContainer,
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = ClayContainer,
    background = SurfaceLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = SurfaceDim,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FDBA6),
    onPrimary = Color(0xFF003823),
    primaryContainer = ForestDark,
    onPrimaryContainer = ForestContainer,
    secondary = Color(0xFFFFB68C),
    onSecondary = Color(0xFF522300),
    background = SurfaceDark,
    onBackground = Color(0xFFE1E3DD),
    surface = SurfaceDark,
    onSurface = Color(0xFFE1E3DD),
    surfaceVariant = Color(0xFF414941),
    outline = Color(0xFF8B938A),
)

private val CabinTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun CabinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CabinTypography,
        content = content,
    )
}
