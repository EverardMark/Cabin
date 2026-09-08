package com.cabin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Cabin Soft is a light design on a pale green-to-cream ground, so the Material
 * scheme is light-only and maps the design tokens onto Material roles for the
 * few stock components still in use (dialogs, text fields in the post form).
 */
private val SoftColors = lightColorScheme(
    primary = SoftInk,
    onPrimary = Color.White,
    primaryContainer = SoftTile,
    onPrimaryContainer = SoftText,
    secondary = SoftTextSoft,
    onSecondary = Color.White,
    secondaryContainer = SoftPale,
    onSecondaryContainer = SoftText,
    tertiary = SoftClay,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E3D3),
    background = SoftBackgroundMid,
    onBackground = SoftText,
    surface = Color.White,
    onSurface = SoftText,
    surfaceVariant = SoftTile,
    onSurfaceVariant = SoftSecondary,
    outline = SoftOutline,
    outlineVariant = SoftDivider,
    error = SoftRed,
    onError = Color.White,
)

private val SoftTypography = Typography(
    displayLarge = SoftType.display,
    headlineLarge = SoftType.title,
    headlineMedium = SoftType.heading,
    headlineSmall = SoftType.screenTitle,
    titleLarge = SoftType.cardTitle,
    titleMedium = SoftType.body,
    titleSmall = soft(15, FontWeight.Normal),
    bodyLarge = SoftType.bodyLight,
    bodyMedium = SoftType.small,
    bodySmall = SoftType.caption,
    labelLarge = SoftType.button,
    labelMedium = SoftType.footnote,
    labelSmall = SoftType.tag,
)

@Composable
fun CabinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoftColors,
        typography = SoftTypography,
        content = content,
    )
}
