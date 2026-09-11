package com.azurpilot.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.azurpilot.mobile.R

/**
 * 字体：Inter —— SF Pro 的最佳开源替身。
 *
 * 之前用系统默认字体（Roboto），这是整界面「不像 Apple」的最大破绽：
 * Roboto 的字腔、字重和字距跟 SF Pro 差得很明显。
 *
 * 打包的是**可变字体**（Google Fonts 只提供这一种），所以同一个
 * res/font/inter.ttf 通过 FontVariation 声明出多个字重。
 * 可变字体需要 API 26+，与 minSdk 一致。
 */
private fun interAt(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AppSans = FontFamily(
    interAt(400), // Regular
    interAt(500), // Medium
    interAt(600), // SemiBold
    interAt(700), // Bold
)

/** 日志用等宽 —— 时间戳与等级列才能严格对齐 */
private fun monoAt(weight: Int) = Font(
    resId = R.font.jetbrains_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AppMono = FontFamily(
    monoAt(400),
    monoAt(500),
    monoAt(700),
)

/**
 * 排版阶梯对齐 iOS Human Interface 的字号：
 *   Large Title 34 / Title 22 / Headline 17 Semibold / Body 17 /
 *   Callout 16 / Subhead 15 / Footnote 13 / Caption 12
 *
 * 数字统一开 tabular figures（tnum），30s 刷新时宽度不变、不会抖。
 */
private fun numeric(
    weight: FontWeight,
    size: Int,
    tracking: Double,
): TextStyle = TextStyle(
    fontFamily = AppSans,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
)

/** Hero 数值：石油 / 物资 —— 对齐 iOS「健康」那种大数字，但用 SemiBold 而不是超粗 */
val NumeralHero = numeric(FontWeight.SemiBold, 26, -0.6)

/** 列表行的数值：iOS Body 尺寸 */
val NumeralValue = numeric(FontWeight.SemiBold, 17, -0.2)

/** 次要数值（上限、范围） */
val NumeralSmall = numeric(FontWeight.Medium, 13, 0.0)

/**
 * 数值**正下方**的说明字（上限 / 总行动力）。
 *
 * 11sp 是 HIG 允许的最小字号，也正是这里该用的量级 ——
 * 它是主数值的注解，不是第二个数值，所以要比 [NumeralSmall] 再低一档。
 */
val NumeralCaption = numeric(FontWeight.Normal, 11, 0.1)

/** 兼容旧调用点 */
val NumeralLarge = NumeralHero
val NumeralMedium = NumeralValue
val NumeralCompact = NumeralValue

val AppTypography = Typography(
    // iOS Large Title —— 页面顶部的大标题
    //
    // ⚠️ 字距原来是 **-0.7sp，符号是反的**。HIG 的字距表里 34pt 是 **+0.37pt（正值）**，
    // 负字距会把大标题挤扁 —— 这是全工程最明显的一处排版错误。
    //
    // 这里按 HIG 的 Large Title 档来做：**34pt / Bold / +0.37**。
    // 但**用的是 Inter 不是 SF Pro**，Inter 原始字距更紧，所以数值当量级参考，
    // 真机上如果显紧就再往上加，别照抄。
    headlineMedium = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = 0.37.sp,
        lineHeight = 41.sp, // HIG：34pt → 41pt 行高
    ),
    // 状态卡的实例名 —— iOS Headline 17（原来是 titleMedium 14sp，偏小一档）
    titleLarge = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.38.sp,
    ),
    // 区块标题 —— iOS 分组列表的 section header 观感
    titleMedium = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = -0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.sp,
    ),
    // 资源名 / 列表主文本 —— iOS Body 17 / 行高 22
    bodyLarge = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = -0.41.sp, // HIG：17pt → -0.41
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = -0.24.sp, // HIG：15pt → -0.24
        lineHeight = 20.sp,
    ),
    // 相对时间等次要信息 —— iOS Footnote 13 / 行高 18
    bodySmall = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = -0.08.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = -0.08.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
    ),
    // ⚠️ 这个槽原来没覆写，于是落到 M3 默认的 **Roboto** ——
    // 也就是说全 app 只有气泡按钮那一处字体不一样（HIG：字体家族越少越好）。
    labelLarge = TextStyle(
        fontFamily = AppSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = -0.24.sp,
    ),
)

/**
 * 日志行：JetBrains Mono 11 / 行高 1.55。
 *
 * 用 11sp 而不是 12sp —— 因为要完整放下 PC 端的
 * `INFO │ 03:24:37.292 │ ` 这段前缀（19 个字符），字号大一档正文就被挤没了。
 */
val LogLineStyle = TextStyle(
    fontFamily = AppMono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.sp,
)
