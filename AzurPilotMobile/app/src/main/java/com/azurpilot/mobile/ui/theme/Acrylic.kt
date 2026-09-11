package com.azurpilot.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「轻薄亚克力」的两条实现要点：
 *  1. 不用模糊（Android 没有 CSS 的 backdrop-filter，真模糊在滚动列表上是掉帧主因），
 *     轻薄感来自 **细高光边 + 顶部 1dp 反光 + 恰当透明度**。
 *  2. 背景用径向渐变模拟光斑，而不是模糊圆 —— 全 API 级别一致，不会在低版本变成硬边圆。
 */

/** 光感基底：纵向渐变 + 两枚缓慢偏移的色斑 */
fun Modifier.ambientBackground(tokens: AcrylicTokens): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(tokens.bgTop, tokens.bgBottom)))

    val r1 = size.minDimension * 0.95f
    val c1 = Offset(size.width * 0.08f, size.height * 0.04f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tokens.blobPrimary, Color.Transparent),
            center = c1,
            radius = r1,
        ),
        radius = r1,
        center = c1,
    )

    val r2 = size.minDimension * 0.80f
    val c2 = Offset(size.width * 1.02f, size.height * 0.30f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tokens.blobSecondary, Color.Transparent),
            center = c2,
            radius = r2,
        ),
        radius = r2,
        center = c2,
    )
}

/**
 * 亚克力面板。
 *
 * 关键取舍：**默认不加阴影**。
 * iOS 的卡片（Settings / Health 那种分组列表）几乎不用投影，层次完全靠
 * 「背景灰 / 卡片白」的明度差建立。之前每张卡都挂一层 drop shadow，
 * 那是典型的 Material 观感，也是「不像 Apple」的原因之一。
 *
 * 需要浮起来的元素（Tab 栏、悬浮按钮）才显式传 elevation。
 *
 * @param strong 更实的填充，用于 Tab Bar 这类要压住滚动内容的地方
 */
@Composable
fun AcrylicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    strong: Boolean = false,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 0.5.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = AppTheme.acrylic

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color(0x0A000000),
                spotColor = Color(0x14000000),
            )
            .clip(shape)
            .background(if (strong) t.panelStrong else t.panel)
            .drawWithContent {
                drawContent()
                // 顶部 1dp 反光：光从上方来，做出「薄片切边」
                val h = 1.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(t.panelHighlight, Color.Transparent),
                        startY = 0f,
                        endY = h,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, h),
                )
            }
            .border(borderWidth, t.panelBorder, shape),
        content = content,
    )
}

/** 面板内的分隔线 */
fun Modifier.acrylicDivider(tokens: AcrylicTokens): Modifier = drawBehind {
    val h = 1.dp.toPx()
    drawRect(
        color = tokens.divider,
        topLeft = Offset(0f, size.height - h),
        size = Size(size.width, h),
    )
}
