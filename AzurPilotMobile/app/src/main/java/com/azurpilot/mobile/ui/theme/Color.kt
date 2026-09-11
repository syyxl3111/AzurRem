package com.azurpilot.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// 品牌色：取自启动/停止按钮的实测蓝色（iOS 控制中心那种实心蓝圆）
// ─────────────────────────────────────────────────────────────
val PilotBlue = Color(0xFF3D6FE3)
val PilotBlueLight = Color(0xFF5285F0)
val PilotBlueDeep = Color(0xFF2E5FD8)

// ─────────────────────────────────────────────────────────────
// 浅色模式基底 —— 对齐 iOS systemGroupedBackground (#F2F2F7)
// Apple 的卡片几乎没有阴影，层次全靠「背景灰 / 卡片白」的明度差
// ─────────────────────────────────────────────────────────────
val LightBgTop = Color(0xFFF2F2F7)
val LightBgBottom = Color(0xFFE8E8EF)
val LightPanel = Color(0xC7FFFFFF)          // 分组列表容器：白，留一点通透做亚克力
val LightPanelStrong = Color(0xF7FFFFFF)    // Tab Bar：近乎不透明
val LightPanelBorder = Color(0x12000000)    // 发丝级描边，只用来给面板收边
val LightPanelHighlight = Color(0x99FFFFFF) // 顶部 1dp 反光
val LightTextPrimary = Color(0xFF1C1C1E)

/**
 * 次级 / 三级文字色 —— **按 WCAG 对比度反推出来的，不是凭手感挑的**。
 *
 * 面板实际底色是 `LightPanel(0xC7FFFFFF)` 叠在 `#F2F2F7` 渐变上，最坏情况约 `#FAFAFC`。
 * 原来 `textTertiary = #8E8E93` 在这个底上只有 **≈3.2:1**，
 * 而 HIG / WCAG 对 ≤17pt 的正文要求 **4.5:1** —— 它被用在时间、上限、
 * 未选中 Tab 图标、日志分隔符上，这些都是要读的内容，不是装饰。
 *
 * 一个绕不开的取舍：**在接近纯白的底上，纯灰没法同时满足 4.5:1 和"看得出层级"**。
 * 所以这里把次级也一起压深，给三级腾出可见的档差：
 *
 * | token | 值 | 对比度（on #FAFAFC） |
 * |---|---|---|
 * | textSecondary | `#5F5F65` | ≈ 6.2:1 |
 * | textTertiary  | `#6F6F75` | ≈ 4.8:1 |
 *
 * 深色那边**不用改**：`#98989D` / `#8E8E93` 在深色面板上分别是 ≈5.1:1 / ≈4.5:1，本来就够。
 */
val LightTextSecondary = Color(0xFF5F5F65)
val LightTextTertiary = Color(0xFF6F6F75)
val LightDivider = Color(0x5E3C3C43)        // iOS separator：3C3C43 @ 37%
val LightTrack = Color(0x0F000000)          // 进度条底槽

// ─────────────────────────────────────────────────────────────
// 深色模式基底（独立设计，不是反色）
// ─────────────────────────────────────────────────────────────
val DarkBgTop = Color(0xFF0A0A0C)
val DarkBgBottom = Color(0xFF141418)
val DarkPanel = Color(0x1FFFFFFF)           // 分组列表容器
val DarkPanelStrong = Color(0xF216161A)     // Tab Bar
val DarkPanelBorder = Color(0x1FFFFFFF)
val DarkPanelHighlight = Color(0x2BFFFFFF)
val DarkTextPrimary = Color(0xFFF2F2F7)
val DarkTextSecondary = Color(0xFF98989D)
val DarkTextTertiary = Color(0xFF8E8E93)
/**
 * 深色分隔线。
 *
 * 原来是 `0x99545458`（@60%），约为 iOS 深色 separator（≈30%）的**两倍重** ——
 * 在深色面板上会看出一条条明显的横杠，而不是"发丝"。
 * HIG 里 separator 只有**一档**语义色，不该靠加深来表示层级。
 */
val DarkDivider = Color(0x4D545458)         // ≈30%
val DarkTrack = Color(0x1FFFFFFF)

// ─────────────────────────────────────────────────────────────
// 语义色（iOS system colors）
// ─────────────────────────────────────────────────────────────
val DangerRed = Color(0xFFFF3B30)
val WarningAmber = Color(0xFFFF9500)
val SuccessGreen = Color(0xFF34C759)

/**
 * 资源配色：把原配置里的高饱和纯色（#0000FF / #00BFFF / #33FFFF …）
 * 换成 Apple 系统色 —— 保留色相，用户仍能一眼认出，但在浅色亚克力上不刺眼。
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
