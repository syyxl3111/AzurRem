package com.azurpilot.mobile.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.data.RunState
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.syncAgeText
import com.azurpilot.mobile.ui.theme.AcrylicSurface
import com.azurpilot.mobile.ui.theme.AppMono
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.NumeralSmall

/**
 * 顶部状态条：实例名 + 运行态 + 当前任务 + 上次同步。
 * 吸顶，亚克力质感。
 */
@Composable
fun StatusCard(
    instance: String,
    running: Boolean,
    stateCode: Int,
    currentTask: String,
    lastSyncAt: Long,
    connected: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.acrylic

    val dotColor = when {
        !connected -> t.textTertiary
        stateCode == RunState.ERROR -> t.danger
        stateCode == RunState.UPDATING -> t.warning
        running -> t.success
        else -> t.textTertiary
    }
    val stateText = if (!connected) "未连接" else RunState.label(stateCode)

    AcrylicSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(color = dotColor, pulsing = connected && running, size = 9.dp)

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = instance,
                        style = MaterialTheme.typography.titleMedium,
                        color = t.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = dotColor,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (connected) "$currentTask · ${syncAgeText(lastSyncAt)}" else "连接失败，正在重试…",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                Box(Modifier.alpha(if (refreshing) 0.35f else 1f)) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = "刷新",
                        tint = t.textSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

/** 状态光点：运行中缓慢呼吸 */
@Composable
fun PulsingDot(color: Color, pulsing: Boolean, size: Dp) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "dot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotAlpha",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * 页面大标题。
 *
 * HIG `toolbars.md`：*Use a large title to help people stay oriented as they navigate
 * and scroll.* —— 之前 5 个 Tab 页里有 3 个（主页/任务/统计）**完全没有标题**，
 * 加上 Tab 栏当时还是纯图标，用户在页面上没有任何文字能确认自己在哪。
 *
 * 加了 `semantics { heading() }`，读屏可以按标题跳转。
 * ⚠️ 滚动折叠（滚下去变小、滚回顶部变大）还没做 —— HIG 的原话里有这一段，
 * 属于下一步的打磨项。
 */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    val t = AppTheme.acrylic
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = t.textPrimary,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

/**
 * 区块小标题 —— 对齐 iOS 分组列表的 section header：
 * 13sp SemiBold、次要色、左缩进与行内容对齐、上方留白大于下方。
 * 也带 `heading()` 语义，方便读屏按标题跳。
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = AppTheme.acrylic
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = t.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** 极简计数徽标 */
@Composable
fun CountBadge(count: Int) {
    val t = AppTheme.acrylic
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(t.track)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = count.toString(),
            style = NumeralSmall.copy(fontFamily = AppMono),
            color = t.textSecondary,
        )
    }
}
