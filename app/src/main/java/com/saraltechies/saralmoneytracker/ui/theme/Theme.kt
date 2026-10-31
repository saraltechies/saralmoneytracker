package com.saraltechies.saralmoneytracker.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.saraltechies.saralmoneytracker.data.ThemeMode

// Saral Techies brand palette, taken from saraltechies.com.
val BrandBlue = Color(0xFF0D6EFD)
val BrandBlueDark = Color(0xFF0A58CA)
val BrandNavy = Color(0xFF0D1B4A)
val BrandInk = Color(0xFF1A1A2E)
val BrandSlate = Color(0xFF64748B)
val BrandMist = Color(0xFFF0F6FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = BrandInk,
    background = Color(0xFFF8FAFF),
    onBackground = BrandInk,
    surface = Color.White,
    onSurface = BrandInk,
    surfaceVariant = BrandMist,
    onSurfaceVariant = BrandSlate,
    surfaceContainer = BrandMist,
    outline = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = BrandNavy,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF93C5FD),
    onSecondary = BrandNavy,
    secondaryContainer = Color(0xFF1E2A55),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B1230),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F1838),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1A2550),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF141E44),
    outline = Color(0xFF334155),
)

/** Money colours: green in, red out, and a warning amber for budgets running close. */
data class MoneyColors(val income: Color, val expense: Color, val transfer: Color, val warning: Color)

private val LightMoney = MoneyColors(Color(0xFF15803D), Color(0xFFDC2626), BrandSlate, Color(0xFFD97706))
private val DarkMoney = MoneyColors(Color(0xFF4ADE80), Color(0xFFF87171), Color(0xFF94A3B8), Color(0xFFFBBF24))

private val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

object AppTheme {
    val money: MoneyColors
        @Composable @ReadOnlyComposable get() = LocalMoneyColors.current
}

@Composable
fun SaralMoneyTrackerTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // The system bars are transparent (edge-to-edge), so their icons must follow the app's theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
