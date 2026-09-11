package com.azurpilot.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 触发返回的拖拽比例（拖过屏幕宽度 1/3 就回退） */
private const val BACK_THRESHOLD = 0.33f

/** 只有从左边缘这么宽以内起手才算「返回手势」，避免和列表横滑打架 */
private val EDGE_WIDTH = 32.dp

/**
 * 侧滑返回容器。
 *
 * 移动端的返回有三种，这里一次给全：
 *  1. 顶部返回箭头（见 [SubPageScaffold]）
 *  2. 系统返回键 / 手势
 *  3. **从左边缘往右滑**（跟手位移，松手按阈值决定回退还是弹回）
 *
 * 手势判定有意保守：必须从左侧 32dp 内起手，且横向位移大于纵向位移才接管，
 * 否则放行给内部的滚动列表。
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val edgePx = with(density) { EDGE_WIDTH.toPx() }

    BackHandler(enabled = true, onBack = onBack)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxWidth = constraints.maxWidth

        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(maxWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > edgePx) return@awaitEachGesture

                        var dragging = false
                        var dx = 0f
                        var dy = 0f
                        val slop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val delta = change.positionChange()
                            dx += delta.x
                            dy += delta.y

                            if (!dragging) {
                                when {
                                    abs(dx) > slop && abs(dx) > abs(dy) -> dragging = true
                                    // 明显是竖向滚动 —— 放行，不抢手势
                                    abs(dy) > slop -> break
                                }
                            }
                            if (dragging) {
                                change.consume()
                                offset = dx.coerceAtLeast(0f)
                            }
                        }

                        if (dragging) {
                            if (offset > maxWidth * BACK_THRESHOLD) {
                                scope.launch {
                                    animate(
                                        initialValue = offset,
                                        targetValue = maxWidth.toFloat(),
                                        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
                                    ) { value, _ -> offset = value }
                                    onBack()
                                }
                            } else {
                                scope.launch {
                                    animate(
                                        initialValue = offset,
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
                                    ) { value, _ -> offset = value }
                                }
                            }
                        }
                    }
                },
        ) {
            content()
        }
    }
}

/**
 * 子页面骨架：左上角返回箭头 + 标题，下面是内容。
 * 对齐 iOS 的 push 页面 —— 压住 Tab 栏，只保留一条返回路径。
 */
@Composable
fun SubPageScaffold(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = AppTheme.acrylic

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = insets.horizontal, end = insets.horizontal, top = insets.top),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = AppIcons.ChevronLeft,
                    contentDescription = "返回",
                    tint = t.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = t.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions,
            )
        }

        content()
    }
}

/** 顶部一个空占位，用于骨架屏 */
@Composable
fun ScaffoldSpacer(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}
