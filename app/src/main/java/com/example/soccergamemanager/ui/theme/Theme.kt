package com.example.soccergamemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = McFarlandBlue,
    onPrimary = IceWhite,
    primaryContainer = McFarlandBlueLight,
    onPrimaryContainer = BadgeBlack,
    secondary = BadgeBlack,
    onSecondary = IceWhite,
    secondaryContainer = Frost,
    onSecondaryContainer = BadgeBlack,
    tertiary = McFarlandBlueDark,
    onTertiary = IceWhite,
    background = Frost,
    onBackground = BadgeBlack,
    surface = IceWhite,
    onSurface = BadgeBlack,
    surfaceVariant = McFarlandBlueLight,
    onSurfaceVariant = BadgeBlackSoft,
    outline = DividerGray,
    outlineVariant = DividerGray,
    inverseSurface = BadgeBlack,
    inverseOnSurface = IceWhite,
    error = GoalRed,
    onError = IceWhite,
)

private val DarkColors = darkColorScheme(
    primary = McFarlandBlueLight,
    onPrimary = BadgeBlack,
    primaryContainer = McFarlandBlueDark,
    onPrimaryContainer = IceWhite,
    secondary = IceWhite,
    onSecondary = BadgeBlack,
    secondaryContainer = BadgeBlackSoft,
    onSecondaryContainer = IceWhite,
    tertiary = McFarlandBlue,
    onTertiary = IceWhite,
    background = BadgeBlack,
    onBackground = IceWhite,
    surface = BadgeBlackSoft,
    onSurface = IceWhite,
    surfaceVariant = McFarlandBlueDark,
    onSurfaceVariant = IceWhite,
    outline = Slate,
    outlineVariant = BadgeBlackSoft,
    inverseSurface = IceWhite,
    inverseOnSurface = BadgeBlack,
    error = GoalRed,
    onError = IceWhite,
)

@Composable
fun SoccerGameManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
