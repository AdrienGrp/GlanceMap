package com.glancemap.glancemapcompanionapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = TrailGreen80,
        onPrimary = CompanionDarkBackground,
        primaryContainer = TrailGreenDark,
        onPrimaryContainer = TrailGreen90,
        secondary = MossGrey80,
        onSecondary = CompanionDarkBackground,
        secondaryContainer = CompanionDarkSecondaryContainer,
        onSecondaryContainer = CompanionDarkOnSecondaryContainer,
        tertiary = SunAmber80,
        onTertiary = CompanionDarkBackground,
        tertiaryContainer = SunAmberDark,
        onTertiaryContainer = SunAmber80,
        background = CompanionDarkBackground,
        onBackground = CompanionDarkOnSurface,
        surface = CompanionDarkSurface,
        onSurface = CompanionDarkOnSurface,
        surfaceVariant = CompanionDarkSurfaceVariant,
        onSurfaceVariant = CompanionDarkOnSurfaceVariant,
        outline = CompanionDarkOutline,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = TrailGreen40,
        onPrimary = CompanionLightSurface,
        primaryContainer = TrailGreen90,
        onPrimaryContainer = CompanionLightOnSecondaryContainer,
        secondary = MossGrey40,
        onSecondary = CompanionLightSurface,
        secondaryContainer = CompanionLightSecondaryContainer,
        onSecondaryContainer = CompanionLightOnSecondaryContainer,
        tertiary = SunAmber40,
        onTertiary = CompanionLightSurface,
        tertiaryContainer = CompanionLightTertiaryContainer,
        onTertiaryContainer = CompanionLightOnTertiaryContainer,
        background = CompanionLightBackground,
        onBackground = CompanionLightOnSurface,
        surface = CompanionLightSurface,
        onSurface = CompanionLightOnSurface,
        surfaceVariant = CompanionLightSurfaceVariant,
        onSurfaceVariant = CompanionLightOnSurfaceVariant,
    )

@Composable
fun GlanceMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
