package com.azurpilot.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.data.ResourceItem
import com.azurpilot.mobile.ui.formatNumber
import com.azurpilot.mobile.ui.theme.AppTypography
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.NumeralCaption
import com.azurpilot.mobile.ui.theme.NumeralSmall
import com.azurpilot.mobile.ui.theme.NumeralValue
import com.azurpilot.mobile.ui.theme.ResourceColors
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text

/** 分组列表的行内边距（左右一致，分隔线也按这个值内缩） */
private val ROW_PADDING = 16.dp

/**
 * 资源行 —— iOS **分组内嵌列表**里的一行。
 *
 * 结构**刻意做成完全统一的**，不管这项资源有没有上限：
 *
 * ```
 * ● 石油                      2,879
 *   1 小时前             上限 11,200
 *
 * ● 行动力                      143
 *   3 分钟前         总行动力 2,993
 * ```
 *
 * 五个决定：
 *  1. **每行都是两行文字、同样的上下留白**，所以整列高度天然相等。
 *     之前带上限的资源多一条进度条、比别的行高出一截，一列看下来参差不齐。
 *  2. **数字相对整个两行块垂直居中**，而不是只跟第一行对齐。
 *     数字比标签大一号，如果只在第一行里居中，字号一变基线就跟着飘 ——
 *     实际观感就是"从某一行开始数字偏上了"。
 *  3. **上限 / 总行动力放在数字正下方、和数字右对齐**。
 *     它是**这个数值的注解**（"这个数相对什么而言"），贴着数放才读得通；
 *     丢到左边第二行会跟"上次更新时间"混成同一列，两件不相干的事被并排比较。
 *  4. 注解用 [NumeralCaption]（11sp），比主数值小两档 ——
 *     明确它是注解，不是第二个数值。
 *  5. **不放进度条**。石油/物资的上限是 OCR 出来的、会变，条子只是在
 *     「看起来像个仪表盘」；真正要看的是数字和「上次更新是什么时候」。
 */
@Composable
fun ResourceRow(item: ResourceItem) {
    val t = AppTheme.colors
    val color = ResourceColors.of(item.key, t.isDark)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ROW_PADDING, end = ROW_PADDING, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color)
        Spacer(Modifier.width(11.dp))

        // 左列：名称 + 上次更新时间
        Column(Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = AppTypography.bodyLarge,
                color = if (item.value == null) t.textTertiary else t.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.ageText,
                style = NumeralCaption,
                color = t.textTertiary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(12.dp))

        // 右列：数值 + 它的注解，右对齐成一列
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (item.value == null) "未采集" else formatNumber(item.value),
                style = if (item.value == null) {
                    AppTypography.bodyLarge
                } else {
                    NumeralValue
                },
                color = if (item.value == null) t.textTertiary else t.textPrimary,
                maxLines = 1,
            )
            ValueCaption(item = item, overflowColor = t.warning)
        }
    }
}

/**
 * 分组列表内的发丝分隔线。
 * iOS 会把它从左侧内缩到与内容对齐的位置，而不是拉满整宽。
 */
@Composable
fun Hairline(startInset: Dp = ROW_PADDING) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = AppTheme.colors.divider,
        modifier = Modifier.padding(start = startInset),
    )
}

/** 资源色点 —— 沿用你原图里的圆点视觉 */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * 进度条：填充 = min(Value/Limit, 1.0)，**绝不溢出**。
 * 石油/物资过了上限仍能继续存，所以数值会大于上限，但条只满格。
 */
@Composable
fun ResourceProgress(
    progress: Float,
    color: Color,
    track: Color,
    height: Dp = 5.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550),
        label = "progress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track),
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * 数值正下方那行注解（右对齐，跟数字同一列）。
 *
 * 三个分支，都用同一套浅灰（`textTertiary`）+ [NumeralCaption]：
 * 它们是**同一层级的信息** —— 一个静态的、参考用的数字，
 * 既是"这个数相对什么而言"的说明，就不该和主数值抢注意力。
 *
 *  - 有上限：`上限 11,200`；超上限时把「上限」两个字染警示色
 *    （**不再画满格进度条** —— 数值照实显示，只是提个醒）
 *  - 只有总量：`总行动力 2,993`（行动力的 total = 当前 + 未开箱）
 *  - 都没有：不占位
 */
@Composable
private fun ValueCaption(item: ResourceItem, overflowColor: Color) {
    val t = AppTheme.colors

    item.limit?.let { limit ->
        if (item.overflow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上限 ", style = NumeralCaption, color = overflowColor, maxLines = 1)
                Text(
                    formatNumber(limit),
                    style = NumeralCaption,
                    color = t.textTertiary,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = "上限 ${formatNumber(limit)}",
                style = NumeralCaption,
                color = t.textTertiary,
                maxLines = 1,
            )
        }
        return
    }

    item.total?.let { total ->
        Text(
            text = "总行动力 ${formatNumber(total)}",
            style = NumeralCaption,
            color = t.textTertiary,
            maxLines = 1,
        )
    }
}
