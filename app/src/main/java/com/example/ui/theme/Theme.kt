package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JoshFitColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = PureWhite,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = PureWhite,
    secondary = FlameOrange,
    onSecondary = PureWhite,
    secondaryContainer = CharcoalElevated,
    onSecondaryContainer = PureWhite,
    tertiary = RestCyan,
    onTertiary = CharcoalBg,
    background = CharcoalBg,
    onBackground = PureWhite,
    surface = CharcoalSurface,
    onSurface = PureWhite,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = MutedSilver,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalCard,
    surfaceContainerHigh = CharcoalElevated,
    outline = CharcoalOutline,
    error = CrimsonBright,
    onError = PureWhite
)

private val JoshFitShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JoshFitTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JoshFitColorScheme,
        typography = JoshFitTypography,
        shapes = JoshFitShapes,
        content = content
    )
}
