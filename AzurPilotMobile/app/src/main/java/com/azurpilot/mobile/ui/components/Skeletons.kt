package com.azurpilot.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.MiuixSurface

/**
 * 骨架屏。
 *
 * 设计取舍：
 *  1. **形状要像真内容**。一整块灰只会让人觉得"卡住了"；照着真实行的高矮和
 *     元素位置摆几个条，大脑会自动把它补成内容，观感就从"卡"变成"在加载"。
 *  2. **用扫光而不是呼吸**。呼吸（整块透明度来回变）在浅色背景上很像闪烁故障；
 *     一道斜向高光扫过去更接近 iOS 的观感，也更"安静"。
 *  3. 骨架块本身**不画边框**（真卡片有），否则一屏框线会比内容还抢眼。
 */
@Composable
fun SkeletonBar(
    width: Dp?,
    height: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 5.dp,
) {
    val t = AppTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                drawRect(color = t.track, size = size)

                // 斜向扫光：一道比块宽稍窄的高光从左扫到右
                val w = size.width
                val x = sweep * (w * 2f) - w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            t.panelHighlight.copy(alpha = 0.85f),
                            Color.Transparent,
                        ),
                        start = Offset(x, 0f),
                        end = Offset(x + w * 0.8f, size.height),
                    ),
                    topLeft = Offset.Zero,
                    size = Size(w, size.height),
                )
            },
    )
}

/**
 * 资源列表骨架 —— 照着真实资源行摆位：
 * 左侧色点 + 名称，右侧大号数字，下方（前两条）一条进度条和一行小字。
 */
@Composable
fun ResourceListSkeleton(modifier: Modifier = Modifier) {
    val t = AppTheme.colors

    MiuixSurface(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            repeat(5) { index ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(t.track),
                        )
                        Spacer(Modifier.width(11.dp))
                        SkeletonBar(width = if (index % 2 == 0) 52.dp else 74.dp, height = 14.dp)
                        Spacer(Modifier.weight(1f))
                        SkeletonBar(width = if (index % 2 == 0) 92.dp else 68.dp, height = 20.dp)
                    }
                    // 只有前两条带进度条，和真实数据一样（石油/物资才有上限）
                    if (index < 2) {
                        Spacer(Modifier.height(11.dp))
                        SkeletonBar(
                            width = null,
                            height = 6.dp,
                            corner = 3.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(9.dp))
                        Row {
                            SkeletonBar(width = 66.dp, height = 10.dp)
                            Spacer(Modifier.weight(1f))
                            SkeletonBar(width = 44.dp, height = 10.dp)
                        }
                    }
                }
                if (index != 4) Hairline()
            }
        }
    }
}

/** 统计页骨架：一张高卡片 + 若干数据行 */
@Composable
fun StatsSectionSkeleton(
    rows: Int = 4,
    showChart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    MiuixSurface(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (showChart) {
                SkeletonBar(width = 150.dp, height = 22.dp)
                SkeletonBar(
                    width = null,
                    height = 110.dp,
                    corner = 10.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            repeat(rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBar(width = if (it % 2 == 0) 78.dp else 96.dp, height = 12.dp)
                    Spacer(Modifier.weight(1f))
                    SkeletonBar(width = if (it % 2 == 0) 54.dp else 72.dp, height = 12.dp)
                }
            }
        }
    }
}

/** 配置树骨架：分组标题条 */
@Composable
fun ConfigTreeSkeleton(groups: Int = 6, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        repeat(groups) {
            MiuixSurface(Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBar(width = if (it % 2 == 0) 104.dp else 86.dp, height = 14.dp)
                    Spacer(Modifier.weight(1f))
                    SkeletonBar(width = 14.dp, height = 14.dp, corner = 4.dp)
                }
            }
        }
    }
}
