package com.azurpilot.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/** MIUIX theme seed and app-specific semantic states. */
val AzurPrimary = Color(0xFF3482FF)
val WarningAmber = Color(0xFFFF8F00)
val SuccessGreen = Color(0xFF2E9B5F)

/**
 * 资源配色：把原配置里的高饱和纯色（#0000FF / #00BFFF / #33FFFF …）
 * 保留各资源的语义色相，并为深浅主题分别提供可读的色值。
 *
 * light / dark 分开给值，因为「石油黑」在深色背景上会直接消失。
 */
data class ResColor(val light: Color, val dark: Color)

object ResourceColors {
    private val map: Map<String, ResColor> = mapOf(
        // key 与 AzurPilot 的 Dashboard.<Key> 一一对应
        "Oil" to ResColor(Color(0xFF1C1C1E), Color(0xFFE5E5EA)),          // 石油：保留「石油黑」，深色下反白
        "Coin" to ResColor(Color(0xFFFF9F0A), Color(0xFFFFA426)),         // 物资 systemOrange
        "Gem" to ResColor(Color(0xFFFF453A), Color(0xFFFF6961)),          // 钻石 systemRed
        "Pt" to ResColor(Color(0xFF32ADE6), Color(0xFF40B8EC)),           // 活动PT systemTeal
        "Cube" to ResColor(Color(0xFF64D2FF), Color(0xFF70D7FF)),         // 魔方 systemCyan
        "ActionPoint" to ResColor(Color(0xFF0A84FF), Color(0xFF3D9BFF)),  // 行动力 systemBlue
        "YellowCoin" to ResColor(Color(0xFFFF9500), Color(0xFFFFA426)),   // 大世界黄币
        "PurpleCoin" to ResColor(Color(0xFFBF5AF2), Color(0xFFCB78F5)),   // 大世界紫币 systemPurple
        "Core" to ResColor(Color(0xFF8E8E93), Color(0xFF98989D)),         // 核心数据 systemGray
        "Medal" to ResColor(Color(0xFFFFD60A), Color(0xFFFFDE33)),        // 勋章 systemYellow
        "Merit" to ResColor(Color(0xFFFFB340), Color(0xFFFFC061)),        // 功勋
        "GuildCoin" to ResColor(Color(0xFF98989D), Color(0xFFA1A1A6)),    // 舰队币
    )

    private val fallback = ResColor(Color(0xFF8E8E93), Color(0xFF98989D))

    fun of(key: String, dark: Boolean): Color {
        val c = map[key] ?: fallback
        return if (dark) c.dark else c.light
    }
}

/** 资源展示顺序 —— 与 AzurPilot 的 module/config/argument/dashboard.yaml 一致 */
val RESOURCE_ORDER = listOf(
    "Oil", "Coin", "Gem", "Pt", "Cube", "ActionPoint",
    "YellowCoin", "PurpleCoin", "Core", "Medal", "Merit", "GuildCoin",
)

/** 中文标签兜底（正常情况下服务端会返回 label，这里只用于顺序表缺失时） */
val RESOURCE_FALLBACK_LABEL = mapOf(
    "Oil" to "石油",
    "Coin" to "物资",
    "Gem" to "钻石",
    "Pt" to "活动PT",
    "Cube" to "魔方",
    "ActionPoint" to "行动力",
    "YellowCoin" to "大世界黄币",
    "PurpleCoin" to "大世界紫币",
    "Core" to "核心数据",
    "Medal" to "勋章",
    "Merit" to "功勋",
    "GuildCoin" to "舰队币",
)
