package id.bangkumis.dontbroke.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary            = Green80,
    onPrimary          = Green20,
    primaryContainer   = Green30,
    onPrimaryContainer = Green90,
    secondary          = Green70,
    onSecondary        = Green10,
    secondaryContainer = Green20,
    onSecondaryContainer = Green80,
    tertiary           = Green60,
    onTertiary         = Green10,
    background         = DarkSurface,
    onBackground       = Green95,
    surface            = DarkSurface,
    onSurface          = Green95,
    surfaceVariant     = DarkContainer,
    onSurfaceVariant   = Green80,
    outline            = Green40,
)

private val LightColorScheme = lightColorScheme(
    primary            = Green40,
    onPrimary          = Color.White,
    primaryContainer   = Green90,
    onPrimaryContainer = Green10,
    secondary          = Green50,
    onSecondary        = Color.White,
    secondaryContainer = Green95,
    onSecondaryContainer = Green10,
    tertiary           = Green30,
    onTertiary         = Color.White,
    background         = Green99,
    onBackground       = Green10,
    surface            = Green99,
    onSurface          = Green10,
    surfaceVariant     = Green95,
    onSurfaceVariant   = Green30,
    outline            = Green50,
)

@Composable
fun DontBrokeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
