package com.azurpilot.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.data.RunState
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/**
 * 按钮直径。原来是 62dp，整体**缩小 25%**。
 *
 * 注意 48dp 是安卓的最小**触控目标**、HIG 是 44pt —— 46.5dp 仍然达标，
 * 再往下就该给可点区单独留 48dp 的隐形热区了。
 */
private val FAB_SIZE = 46.5.dp

/**
 * 图标尺寸也跟着缩同样的比例。
 * 只缩圆不缩图标的话，图标会相对变大，整个按钮看起来像"被框住的图标"而不是按钮。
 */
private val FAB_ICON_RUNNING = 25.5.dp
private val FAB_ICON_IDLE = 22.5.dp

/** 外圈柔光底 / 旋转弧线的画布直径（都比按钮大一圈） */
private val FAB_GLOW = FAB_SIZE + 13.5.dp
private val FAB_RING = FAB_SIZE + 12.dp
private val FAB_RING_STROKE = 2.25.dp

/**
 * 距屏幕边缘的间距。跟着按钮一起按 0.75 缩，**保持"按钮 : 边距"的比例不变** ——
 * 只缩按钮不缩边距的话，按钮会显得比原来更"往里缩"。
 */
private val EDGE_MARGIN = 13.5.dp

/**
 * 给悬浮启停按钮留出的额外底部空间。
 *
 * 凡是「底部有长列表 + 右下角有这个按钮」的页面都要加，
 * 否则滚到底时最后一行会被按钮压住。（主页和任务页都用了。）
 */
val FAB_CLEARANCE = FAB_SIZE + EDGE_MARGIN - 2.dp

/**
 * 右下角的启动 / 停止悬浮按钮。
 *
 *  - 停止态：实心蓝圆 + 白色圆角三角
 *  - 运行态：实心蓝圆 + 白色圆角方块（外围一圈缓慢呼吸的光晕）
 *  - 可拖动：松手后吸附到最近的左 / 右边缘，垂直位置记忆
 *
 * 手势是手写的（tap 与 drag 分开判定），因为 detectDragGestures 和 clickable
 * 叠在一起时点击会被吞掉。
 */
@Composable
fun StartStopFab(
    modifier: Modifier = Modifier,
    running: Boolean,
    stateCode: Int,
    busy: Boolean,
    initialOnRight: Boolean,
    initialYFraction: Float,
    topReserved: Dp,
    bottomReserved: Dp,
    onClick: () -> Unit,
    onMoved: (Boolean, Float) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fabPx = with(density) { FAB_SIZE.toPx() }
        val marginPx = with(density) { EDGE_MARGIN.toPx() }
        val topPx = with(density) { topReserved.toPx() }
        val bottomPx = with(density) { bottomReserved.toPx() }

        val maxX = (constraints.maxWidth - fabPx - marginPx * 2f).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - fabPx - marginPx * 2f - topPx - bottomPx)
            .coerceAtLeast(0f)

        var posX by remember { mutableFloatStateOf(0f) }
        var posY by remember { mutableFloatStateOf(0f) }
        var placed by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(maxX, maxY) {
            if (!placed && (maxX > 0f || maxY > 0f)) {
                posX = if (initialOnRight) maxX else 0f
                posY = (initialYFraction * maxY).coerceIn(0f, maxY)
                placed = true
            } else {
                posX = posX.coerceIn(0f, maxX)
                posY = posY.coerceIn(0f, maxY)
            }
        }

        val scale by animateFloatAsState(
            targetValue = if (dragging) 1.07f else 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 700f),
            label = "fabScale",
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (marginPx + posX).roundToInt(),
                        (topPx + marginPx + posY).roundToInt(),
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(FAB_SIZE)
                .pointerInput(maxX, maxY, fabPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var accumX = 0f
                        var accumY = 0f
                        var moved = false
                        val slop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val delta = change.positionChange()
                            accumX += delta.x
                            accumY += delta.y

                            if (!moved && (abs(accumX) > slop || abs(accumY) > slop)) {
                                moved = true
                                dragging = true
                            }
                            if (moved) {
                                change.consume()
                                posX = (posX + delta.x).coerceIn(0f, maxX)
                                posY = (posY + delta.y).coerceIn(0f, maxY)
                            }
                        }

                        if (moved) {
                            dragging = false
                            val goRight = (posX + fabPx / 2f) >= (maxX + fabPx) / 2f
                            val targetX = if (goRight) maxX else 0f
                            onMoved(goRight, if (maxY > 0f) posY / maxY else 0f)
                            scope.launch {
                                animate(
                                    initialValue = posX,
                                    targetValue = targetX,
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                                ) { value, _ -> posX = value }
                            }
                        } else if (!busy) {
                            onClick()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            FabVisual(running = running, stateCode = stateCode, busy = busy)
        }
    }
}

@Composable
private fun FabVisual(running: Boolean, stateCode: Int, busy: Boolean) {
    val t = AppTheme.colors

    // 外圈弧线：运行中时绕着按钮转。
    // 配色沿用主蓝而不是参考图里的绿 —— 绿在这个 App 里是「成功」的语义
    // （轻提示的语气点就是绿的），拿来表示"正在跑"会和状态语义打架。
    val spin = if (running) {
        val transition = rememberInfiniteTransition(label = "fabSpin")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "spinAngle",
        ).value
    } else {
        0f
    }

    Box(contentAlignment = Alignment.Center) {
        if (running) {
            // ⚠️ 必须用 requiredSize 而不是 size：
            // 外层容器是 .size(FAB_SIZE)，会把子项的约束**收窄回按钮直径**，
            // 于是这里 78dp 的画布被压成 62dp、弧线正好落在按钮边缘被盖住
            // （原来那个"呼吸光晕"其实一直没显示出来，就是这个原因）。
            // requiredSize 忽略父约束，而 Box 默认不裁剪，所以能画到按钮外面。
            Box(
                Modifier
                    .requiredSize(FAB_GLOW)
                    .clip(CircleShape)
                    .background(t.accent.copy(alpha = 0.10f)),
            )
            Canvas(Modifier.requiredSize(FAB_RING)) {
                val stroke = FAB_RING_STROKE.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = t.accent.copy(alpha = 0.95f),
                    startAngle = spin,
                    sweepAngle = 78f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Box(
            Modifier
                .size(FAB_SIZE)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    clip = false,
                    spotColor = t.accent.copy(alpha = 0.55f),
                    ambientColor = t.accent.copy(alpha = 0.30f),
                )
                .clip(CircleShape)
                .background(t.accent)
                .alpha(if (busy) 0.55f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (running) AppIcons.Stop else AppIcons.Play,
                contentDescription = if (running) "停止调度器" else "启动调度器",
                tint = Color.White,
                // 方块/三角占图标框约 57%，25.5dp 图标 ⇒ 方块约 14.5dp，占 46.5dp 圆约 31%，
                // 接近参考图的比例（按钮缩小后图标按同比例缩，比例关系不变）
                modifier = Modifier.size(if (running) FAB_ICON_RUNNING else FAB_ICON_IDLE),
            )

            if (stateCode == RunState.ERROR) {
                Box(
                    Modifier
                        .size(FAB_SIZE - 2.25.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, t.warning, CircleShape),
                )
            }
        }
    }
}
