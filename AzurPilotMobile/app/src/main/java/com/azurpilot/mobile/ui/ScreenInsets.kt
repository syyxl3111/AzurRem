package com.azurpilot.mobile.ui

import androidx.compose.ui.unit.Dp

/**
 * 屏幕内容的安全边距。
 *
 * - [horizontal] 左右统一留白
 * - [top] 避开状态栏
 * - [bottom] 避开 Tab 栏 + 手势条（内容可以滚到它下面，但留够空间）
 */
data class ScreenInsets(
    val horizontal: Dp,
    val top: Dp,
    val bottom: Dp,
)
