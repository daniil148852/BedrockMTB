// app/src/main/java/com/bedrockconverter/ui/theme/Theme.kt
package com.bedrockconverter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Minecraft-inspired color palette
object BedrockColors {
    val Primary = Color(0xFF4CAF50)        // Grass green
    val PrimaryDark = Color(0xFF388E3C)
    val Secondary = Color(0xFF795548)       // Dirt brown
    val SecondaryDark = Color(0xFF5D4037)
    val Accent = Color(0xFF00BCD4)          // Diamond cyan
    val Background = Color(0xFF1A1A2E)      // Dark background
    val Surface = Color(0xFF252540)         // Card surface
    val SurfaceVariant = Color(0xFF303050)
    val Error = Color(0xFFE57373)
    val OnPrimary = Color.White
    val OnSecondary = Color.White
    val OnBackground = Color.White
    val OnSurface = Color.White
    val TextPrimary = Color(0xFFE0E0E0)
    val TextSecondary = Color(0xFFB0B0B0)
    val Divider = Color(0xFF404060)
    val Success = Color(0xFF81C784)
    val Warning = Color(0xFFFFB74D)
}

private val DarkColorScheme = darkColorScheme(
    primary = BedrockColors.Primary,
    onPrimary = BedrockColors.OnPrimary,
    primaryContainer = BedrockColors.PrimaryDark,
    secondary = BedrockColors.Secondary,
    onSecondary = BedrockColors.OnSecondary,
    secondaryContainer = BedrockColors.SecondaryDark,
    tertiary = BedrockColors.Accent,
    background = BedrockColors.Background,
    onBackground = BedrockColors.OnBackground,
    surface = BedrockColors.Surface,
    onSurface = BedrockColors.OnSurface,
    surfaceVariant = BedrockColors.SurfaceVariant,
    error = BedrockColors.Error
)

private val LightColorScheme = lightColorScheme(
    primary = BedrockColors.Primary,
    onPrimary = BedrockColors.OnPrimary,
    primaryContainer = BedrockColors.PrimaryDark,
    secondary = BedrockColors.Secondary,
    onSecondary = BedrockColors.OnSecondary,
    tertiary = BedrockColors.Accent,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE8E8E8),
    error = BedrockColors.Error
)

object BedrockTypography {
    val titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.5.sp
    )
    
    val titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    )
    
    val titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    )
    
    val bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
    
    val bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
    
    val labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
    
    val labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
}

object BedrockShapes {
    val small = RoundedCornerShape(4.dp)
    val medium = RoundedCornerShape(8.dp)
    val large = RoundedCornerShape(12.dp)
    val extraLarge = RoundedCornerShape(16.dp)
}

@Composable
fun BedrockConverterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = BedrockTypography.titleLarge,
            titleMedium = BedrockTypography.titleMedium,
            titleSmall = BedrockTypography.titleSmall,
            bodyLarge = BedrockTypography.bodyLarge,
            bodyMedium = BedrockTypography.bodyMedium,
            labelLarge = BedrockTypography.labelLarge,
            labelSmall = BedrockTypography.labelSmall
        ),
        content = content
    )
}