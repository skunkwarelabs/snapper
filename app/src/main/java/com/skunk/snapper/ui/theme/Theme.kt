package com.skunk.snapper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * The wallpaper "key" accent color (Android 12+), read straight from the framework
 * @android:color/system_accent1_500 resource.
 *
 * We deliberately bypass Compose's dynamicLightColorScheme/dynamicDarkColorScheme:
 * on Samsung One UI the wallpaper palette is applied via a fabricated overlay on the
 * system_* resources, and Compose's helpers return the *default* (blue) palette anyway
 * — but reading the resource directly reflects the overlay correctly. Seeding our own
 * scheme from it gives true Material You on Samsung, and works on Pixel/stock too.
 */
@Composable
private fun systemSeedColor(): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return remember {
        runCatching { Color(context.getColor(android.R.color.system_accent1_500)) }.getOrNull()
    }
}

@Composable
fun SnapperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color (Material You) is available on Android 12+.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val seed = if (dynamicColor) systemSeedColor() else null

    val colorScheme = when {
        seed != null -> rememberDynamicColorScheme(
            seedColor = seed,
            isDark = darkTheme,
            isAmoled = false,
            style = PaletteStyle.TonalSpot
        )

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
