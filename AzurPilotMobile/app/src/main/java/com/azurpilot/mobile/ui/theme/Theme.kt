package com.azurpilot.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 亚克力令牌。所有面板/描边/文字色都从这里取，
 * 组件里不允许出现硬编码 hex。
 */
@Immutable
data class AcrylicTokens(
    val isDark: Boolean,
    // 背景
    val bgTop: Color,
    val bgBottom: Color,
    val blobPrimary: Color,
    val blobSecondary: Color,
    // 面板
    val panel: Color,
    val panelStrong: Color,
    val panelBorder: Color,
    val panelHighlight: Color,
    val track: Color,
    /** 日志配色：沿用 PC 端日志窗口的习惯（INFO 绿、时间蓝），靠颜色分列而不是斑马纹 */
    val logInfo: Color,
    val logTime: Color,
    // 文字
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    // 强调
    val accent: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
)

val LightAcrylic = AcrylicTokens(
    isDark = false,
    bgTop = LightBgTop,
    bgBottom = LightBgBottom,
    blobPrimary = PilotBlue.copy(alpha = 0.10f),
    blobSecondary = Color(0xFF32ADE6).copy(alpha = 0.08f),
    panel = LightPanel,
    panelStrong = LightPanelStrong,
    panelBorder = LightPanelBorder,
    panelHighlight = LightPanelHighlight,
    track = LightTrack,
    logInfo = Color(0xFF2E8B3D),
    logTime = Color(0xFF2F7FE0),
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    divider = LightDivider,
    accent = PilotBlue,
    danger = DangerRed,
    warning = WarningAmber,
    success = SuccessGreen,
)

val DarkAcrylic = AcrylicTokens(
    isDark = true,
    bgTop = DarkBgTop,
    bgBottom = DarkBgBottom,
    blobPrimary = PilotBlue.copy(alpha = 0.22f),
    blobSecondary = Color(0xFF32ADE6).copy(alpha = 0.14f),
    panel = DarkPanel,
    panelStrong = DarkPanelStrong,
    panelBorder = DarkPanelBorder,
    panelHighlight = DarkPanelHighlight,
    track = DarkTrack,
    logInfo = Color(0xFF5BD86B),
    logTime = Color(0xFF5AC8FA),
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    divider = DarkDivider,
    accent = Color(0xFF5B87EA),
    danger = DangerRed,
    warning = WarningAmber,
    success = SuccessGreen,
)

val LocalAcrylic = staticCompositionLocalOf { LightAcrylic }

object AppTheme {
    val acrylic: AcrylicTokens
        @Composable @ReadOnlyComposable get() = LocalAcrylic.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkAcrylic else LightAcrylic

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            background = tokens.bgTop,
            onBackground = tokens.textPrimary,
            surface = tokens.bgTop,
            onSurface = tokens.textPrimary,
            error = tokens.danger,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            background = tokens.bgTop,
            onBackground = tokens.textPrimary,
            surface = tokens.bgTop,
            onSurface = tokens.textPrimary,
            error = tokens.danger,
        )
    }

    CompositionLocalProvider(LocalAcrylic provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
