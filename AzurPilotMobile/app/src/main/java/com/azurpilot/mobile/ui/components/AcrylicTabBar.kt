package com.azurpilot.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azurpilot.mobile.ui.Tab
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.theme.AcrylicSurface
import com.azurpilot.mobile.ui.theme.AppSans
import com.azurpilot.mobile.ui.theme.AppTheme

/** Tab 项的高度。与 `AppRoot.TAB_BAR_HEIGHT` 配套（栏高 = 本值 + 上下 padding）。 */
val TAB_ITEM_HEIGHT = 56.dp

/** Tab 文字标签 —— HIG Caption 2（11pt，也是 HIG 允许的最小字号） */
private val TabLabelStyle = TextStyle(
    fontFamily = AppSans,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 0.07.sp,
)

/**
 * 底部 Tab 栏。
 *
 * **带文字标签**（HIG `tab-bars.md`：*Include tab labels to help with navigation.*）。
 * 之前是纯图标，5 个标签的文字只存在于 `contentDescription` 里 ——
 * 视觉上用户没有任何文字可以确认自己在哪，这也是"缺身份识别"的一半原因
 * （另一半是三个页面没有大标题）。标签用 Caption 2 / 11sp，正好是 HIG 的最小字号。
 *
 * 高度从 52dp 提到 56dp 以容纳图标 + 标签；`AppRoot.TAB_BAR_HEIGHT` 同步改。
 */
@Composable
fun AcrylicTabBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Tab.Home to AppIcons.House,
        Tab.Tasks to AppIcons.Checklist,
        Tab.Config to AppIcons.Sliders,
        Tab.Stats to AppIcons.Chart,
        Tab.Settings to AppIcons.Gear,
    )

    AcrylicSurface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        strong = true,
        elevation = 16.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (tab, icon) ->
                TabItem(
                    icon = icon,
                    label = tab.label(),
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = AppTheme.acrylic

    val tint by animateColorAsState(
        targetValue = if (selected) t.accent else t.textTertiary,
        label = "tabTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "tabScale",
    )

    Box(
        modifier = Modifier
            .size(width = 64.dp, height = TAB_ITEM_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            // 用 selectable 而不是 clickable：这样能正确播报「标签页 + 已选中」，
            // 屏幕阅读器不会把 5 个 Tab 读成 5 个普通按钮
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                // 图标本身不再重复标签（下面已经有可见文字了），
                // 交给 selectable 的语义去播报，避免读屏读两遍
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(23.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = TabLabelStyle,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

private fun Tab.label(): String = when (this) {
    Tab.Home -> "主页"
    Tab.Tasks -> "任务"
    Tab.Config -> "配置"
    Tab.Stats -> "统计"
    Tab.Settings -> "设置"
}
