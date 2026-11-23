package com.magikvlc.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape

// Marca MagikVLC: violeta vivo como semilla del esquema oscuro.
val BrandPurple = Color(0xFF8B5CF6)

private val BrandLightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D5FF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = Color(0xFF65558F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF201047),
    tertiary = Color(0xFF82506F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EE),
    onTertiaryContainer = Color(0xFF341329),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color.Black,
)

private val BrandDarkColors = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3B1470),
    onPrimaryContainer = Color(0xFFE9D5FF),
    inversePrimary = Color(0xFF6D28D9),
    secondary = Color(0xFFC9B8E8),
    onSecondary = Color(0xFF2E1B4D),
    secondaryContainer = Color(0xFF43305E),
    onSecondaryContainer = Color(0xFFE7DCF9),
    tertiary = Color(0xFFE18AD4),
    onTertiary = Color(0xFF3D0A33),
    tertiaryContainer = Color(0xFF57204B),
    onTertiaryContainer = Color(0xFFFBD7F3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12121A),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF12121A),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF262633),
    onSurfaceVariant = Color(0xFF9A9AA6),
    surfaceTint = BrandPurple,
    inverseSurface = Color(0xFFEDEDF2),
    inverseOnSurface = Color(0xFF1A1A24),
    surfaceDim = Color(0xFF12121A),
    surfaceBright = Color(0xFF3A3A46),
    surfaceContainerLowest = Color(0xFF0C0C12),
    surfaceContainerLow = Color(0xFF1A1A24),
    surfaceContainer = Color(0xFF1F1F2A),
    surfaceContainerHigh = Color(0xFF262633),
    surfaceContainerHighest = Color(0xFF2E2E3B),
    outline = Color(0xFF8E8A93),
    outlineVariant = Color(0xFF2E2C36),
    scrim = Color(0xFF000000),
)

private val MagikVLCTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

private val MagikVLCShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MagikVLCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        }
        darkTheme -> BrandDarkColors
        else -> BrandLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MagikVLCTypography,
        shapes = MagikVLCShapes,
        content = content,
    )
}
