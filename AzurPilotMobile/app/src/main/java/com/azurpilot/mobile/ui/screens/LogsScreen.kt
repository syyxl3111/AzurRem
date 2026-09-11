package com.azurpilot.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.components.StatusCard
import com.azurpilot.mobile.ui.components.SubPageScaffold
import com.azurpilot.mobile.ui.components.SwipeBackContainer
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.theme.AcrylicSurface
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.LogLineStyle

/** 解析 `2026-09-11 02:25:01.123 | INFO | 消息`，毫秒要保留（PC 端就是这么显示的） */
private val LOG_RE = Regex("""^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})\.(\d{3}) \| (\w+)\s*\| ?(.*)$""")

/** 分隔符：用细竖线而不是 ASCII 的 |，两侧留空格对齐 PC 端的 ` | ` */
private const val PIPE = " │ "

@Composable
fun LogsScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.acrylic
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }

    // 只有「用户主动拖动 + 已经离开底部」才关掉自动滚动。
    // 用 isDragged 而不是 isScrollInProgress —— 后者会被我们自己的程序化滚动触发，
    // 导致一进页面自动滚动就是「关」。
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged, atBottom) {
        if (isDragged && !atBottom) autoScroll = false
    }

    LaunchedEffect(state.logs.size, autoScroll) {
        if (autoScroll && state.logs.isNotEmpty()) {
            listState.scrollToItem(state.logs.lastIndex)
        }
    }

    // 日志现在是设置里的一个入口 → 全屏子页面，三种返回方式都可用
    SwipeBackContainer(onBack = onBack, modifier = modifier) {
        SubPageScaffold(
            title = "日志",
            subtitle = "${state.instance} · ${state.logs.size} 行",
            onBack = onBack,
            insets = insets,
            actions = {
                AutoScrollChip(enabled = autoScroll) { autoScroll = it }
                IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "清空本地日志",
                        tint = t.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        ) {
            AcrylicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(Modifier.fillMaxSize()) {
                    HorizontalDivider(thickness = 0.5.dp, color = t.divider)

                    if (state.logs.isEmpty()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (state.connected) "还没有日志" else "未连接，拿不到日志",
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.textSecondary,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 8.dp,
                                bottom = 14.dp,
                            ),
                        ) {
                            items(state.logs) { line ->
                                LogLine(line)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 日志行 —— 严格对齐 PC 端日志窗口的列序与配色：
 *
 *     INFO │ 03:24:37.292 │ [大世界-地图操作] 地图名称已处理: 西大陆架A
 *     ↑等级   ↑时间（带毫秒）  ↑正文
 *
 * 等级在**前**、时间在后；行与行的区分靠颜色（INFO 绿 / 时间蓝 / WARN 橙 / ERROR 红），
 * 不用斑马纹 —— 斑马纹在浅色主题下会显得很脏，而且 PC 端本来就没这层。
 */
@Composable
private fun LogLine(raw: String) {
    val t = AppTheme.acrylic
    val match = LOG_RE.find(raw)

    if (match == null) {
        // 续行 / 框线分隔符
        Text(
            text = raw,
            style = LogLineStyle,
            color = t.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        )
        return
    }

    // 用下标取组 + 长度校验，而不是 match.destructured：
    // groupValues[0] 是整个匹配、捕获组从 1 开始，解构时极易差一位 ——
    // 之前写成 6 个分量而正则只有 5 个组，真机上直接
    // IndexOutOfBoundsException: No group 6 崩掉。
    val groups = match.groupValues
    if (groups.size < 6) return
    val time = groups[2]
    val millis = groups[3]
    val level = groups[4]
    val message = groups[5]

    val levelColor = when (level) {
        "ERROR", "CRITICAL" -> t.danger
        "WARNING", "WARN" -> t.warning
        "DEBUG" -> t.textTertiary
        else -> t.logInfo
    }
    val bodyColor = when (level) {
        "ERROR", "CRITICAL" -> t.danger
        "WARNING", "WARN" -> t.textPrimary
        else -> t.textPrimary.copy(alpha = 0.88f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 2.dp),
    ) {
        // padEnd + 等宽字体 = 等级列严格对齐，INFO 和 ERROR 不会把后面推歪
        Text(
            text = level.take(5).padEnd(5),
            style = LogLineStyle,
            color = levelColor,
        )
        Text(PIPE, style = LogLineStyle, color = t.textTertiary)
        Text(
            text = "$time.$millis",
            style = LogLineStyle,
            color = t.logTime,
        )
        Text(PIPE, style = LogLineStyle, color = t.textTertiary)
        Text(
            text = message,
            style = LogLineStyle,
            color = bodyColor,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 「自动滚动 开 / 关」胶囊。
 *
 * 触控目标 48dp、**视觉尺寸保持 ~29dp** —— 做法是外面套一层
 * `minimumInteractiveComponentSize()` 的可点 Box，把胶囊居中放进去。
 * 直接给胶囊本身加这个 modifier 会把底色也一起撑大，那就不是"小胶囊"了。
 */
@Composable
private fun AutoScrollChip(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val t = AppTheme.acrylic
    val bg by animateColorAsState(
        targetValue = if (enabled) t.accent.copy(alpha = 0.16f) else t.track,
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (enabled) t.accent else t.textTertiary,
        label = "chipFg",
    )

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle(!enabled) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "自动滚动",
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (enabled) "开" else "关",
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
        }
    }
}
