package bd.nib.songbadpatro.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import bd.nib.songbadpatro.domain.model.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = EditorialGreenPrimary,
    onPrimary = EditorialOnPrimary,
    primaryContainer = EditorialGreenContainer,
    onPrimaryContainer = EditorialOnGreenContainer,
    secondary = EditorialCrimsonSecondary,
    onSecondary = EditorialOnSecondary,
    secondaryContainer = EditorialCrimsonContainer,
    onSecondaryContainer = EditorialOnCrimsonContainer,
    tertiary = EditorialGoldTertiary,
    tertiaryContainer = EditorialGoldContainer,
    onTertiaryContainer = EditorialOnGoldContainer,
    background = NewsprintBackground,
    onBackground = EditorialInk,
    surface = NewsprintSurface,
    onSurface = EditorialInk,
    surfaceVariant = NewsprintSurfaceVariant,
    onSurfaceVariant = EditorialMutedInk,
    outline = EditorialOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreenPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = DarkOnGreenContainer,
    secondary = DarkCrimsonSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkCrimsonContainer,
    onSecondaryContainer = DarkOnCrimsonContainer,
    tertiary = DarkGoldTertiary,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedInk,
    outline = DarkOutline
)

@Composable
fun MyNewsTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
        typography = MyNewsTypography,
        content = content
    )
}
