package com.azurpilot.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 从 MIUIX 配色派生的应用语义色。
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val panelStrong: Color,
    val panelHighlight: Color,
    val track: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
)

private val LocalAppDark = staticCompositionLocalOf { false }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() {
            val colors = MiuixTheme.colorScheme
            return AppColors(
                isDark = LocalAppDark.current,
                background = colors.background,
                surface = colors.surface,
                panelStrong = colors.surfaceContainerHighest,
                panelHighlight = colors.surfaceContainerHigh,
                track = colors.secondaryContainer,
                textPrimary = colors.onSurfaceContainer,
                textSecondary = colors.onSurfaceSecondary,
                textTertiary = colors.onSurfaceVariantSummary,
                divider = colors.dividerLine,
                accent = colors.primary,
                danger = colors.error,
                warning = WarningAmber,
                success = SuccessGreen,
            )
        }
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = AzurPrimary,
            onPrimary = Color.White,
            error = Color(0xFFE94634),
        )
    } else {
        lightColorScheme(
            primary = AzurPrimary,
            onPrimary = Color.White,
            error = Color(0xFFE94634),
        )
    }

    MiuixTheme(colors = scheme, textStyles = AppMiuixTextStyles) {
        CompositionLocalProvider(LocalAppDark provides darkTheme, content = content)
    }
}
