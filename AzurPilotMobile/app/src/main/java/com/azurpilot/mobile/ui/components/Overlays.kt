package com.azurpilot.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.azurpilot.mobile.ui.ToastTone
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.AppTypography
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 顶部轻提示 —— **胶囊**（类似 iOS 灵动岛那类）。
 *
 * 四个刻意的决定：
 *  1. **宽度跟着内容走**，不是整条铺满。铺满的横幅读起来像"通知"或"错误"，
 *     体量太重；胶囊只说一句话就缩回去。
 *  2. **深色**。iOS 的 HUD 类提示都是深色 —— 它是浮在内容之上的系统级消息，
 *     不该被误认为页面里的一张卡片（浅色面板在这个 App 里就是"内容"的意思）。
 *     深色在浅色和深色主题下都能压住背景，不用分两套。
 *  3. 左侧那个**语气圆点**比整条染色克制得多；整条染红在浅色背景上很吵。
 *  4. **能被读屏播报**（`liveRegion`），且**错误提示不自动消失** —— 见下。
 *
 * 无障碍（HIG `accessibility.md › Cognitive`：*Minimize use of time-boxed interface
 * elements … Prefer dismissing views with an explicit action*）：
 *  - 自动消失的时长走 `AccessibilityManager.getRecommendedTimeoutMillis()`，
 *    系统里开了"延长交互时间"的用户会自动拿到更长的时长 —— 这是安卓上这条规则的
 *    标准答案，也是唯一有系统依据的做法。
 *  - **错误不自动消失**：错误恰恰是"需要更长时间处理"的信息，
 *    而且它必须有个显式的关闭动作（右侧 ✕）。
 */
@Composable
fun ToastBar(
    message: String,
    tone: ToastTone,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.colors
    val accent = when (tone) {
        ToastTone.Success -> t.success
        ToastTone.Error -> t.danger
        ToastTone.Info -> t.accent
    }

    val context = LocalContext.current
    val a11y = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    // 触觉反馈跟着提示一起出现 —— 起停、立即执行这些动作的结果，
    // 不看屏幕也应该能感觉到（HIG `feedback.md`：反馈要同时用颜色、文字、声音和触感）。
    // 放在这里而不是每个调用点，是为了让"有提示 = 有触感"成为不变量。
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(message, tone) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    // 错误留在屏幕上等用户处理，其余自动走
    val autoDismiss = tone != ToastTone.Error

    LaunchedEffect(message, tone) {
        if (!autoDismiss) return@LaunchedEffect
        val base = 2600
        val ms = a11y?.getRecommendedTimeoutMillis(base, AccessibilityManager.FLAG_CONTENT_TEXT)
            ?: base
        delay(ms.toLong())
        onDismiss()
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it },
        modifier = modifier,
    ) {
        Row(
            Modifier
                // 文案变长时让胶囊平滑长大，而不是"啪"地跳一下
                .animateContentSize(tween(180))
                .clip(CircleShape)
                .background(HudBackground)
                .border(0.5.dp, HudBorder, CircleShape)
                .widthIn(max = 320.dp)
                .padding(start = 15.dp, end = if (autoDismiss) 15.dp else 4.dp, top = 10.dp, bottom = 10.dp)
                // 不自动消失的那种，点胶囊本身也能关掉
                .then(
                    if (autoDismiss) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                    } else {
                        Modifier
                    },
                )
                // 读屏：提示出现时要被播报；错误用 Assertive（打断当前朗读）
                .semantics {
                    liveRegion = if (tone == ToastTone.Error) {
                        LiveRegionMode.Assertive
                    } else {
                        LiveRegionMode.Polite
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = message,
                style = AppTypography.bodySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!autoDismiss) {
                Spacer(Modifier.width(4.dp))
                // 触控目标撑到 44dp，视觉尺寸保持小
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "关闭提示",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/** 胶囊提示的底色 —— 近黑微透，浅色/深色主题下都能压住背景 */
private val HudBackground = Color(0xE81C1C1E)
private val HudBorder = Color(0x1AFFFFFF)

/**
 * 模态确认弹窗。
 *
 * 用于**有副作用、不可轻易撤销**的动作：停调度器会按配置收尾（回主页/关游戏/关模拟器）。
 * 和气泡确认的分工：气泡用于"点错了也无所谓"的轻动作（立即执行某个任务），
 * 模态用于"点错了要收拾残局"的重动作。这是 iOS 的一贯分寸。
 *
 * 点遮罩可取消 —— 但**危险动作不允许点遮罩确认**，必须明确点到按钮。
 */
@Composable
fun ModalConfirmDialog(
    title: String,
    detail: String,
    confirmText: String,
    cancelText: String = "取消",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = AppTheme.colors
    WindowDialog(
        show = true,
        title = title,
        summary = detail,
        onDismissRequest = onCancel,
    ) {
        if (danger) {
            Icon(
                imageVector = AppIcons.Warning,
                contentDescription = null,
                tint = t.danger,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DialogAction(
                text = cancelText,
                color = t.textSecondary,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            DialogAction(
                text = confirmText,
                color = if (danger) t.danger else t.accent,
                bold = true,
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier.height(48.dp),
        minWidth = 0.dp,
        minHeight = 48.dp,
        colors = ButtonDefaults.textButtonColors(
            color = AppTheme.colors.track,
            disabledColor = AppTheme.colors.track,
            textColor = color,
            disabledTextColor = color,
        ),
        textStyle = if (bold) AppTypography.titleSmall else AppTypography.bodyLarge,
    )
}

/**
 * 轻量确认卡（气泡）—— **屏幕居中**。
 *
 * 和 [ModalConfirmDialog] 的分工不靠位置，靠**分量**：
 *   气泡：轻动作（立即执行某个任务），无图标、无标题/正文分层、遮罩很淡，一眼就能答。
 *   模态：重动作（停调度器），有警示图标 + 说明 + 更实的遮罩，逼你看清楚再点。
 *
 * 遮罩比模态淡得多（0.18 vs 0.32）—— 气泡不该让整个页面"沉下去"，
 * 它只是插一句话，不是要拦住你。
 */
@Composable
fun BubbleConfirm(
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = AppTheme.colors
    WindowDialog(show = true, summary = text, onDismissRequest = onCancel) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleButton(
                text = "取消",
                filled = false,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            BubbleButton(
                text = confirmText,
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun BubbleButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = AppTheme.colors
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier.height(44.dp),
        minWidth = 0.dp,
        minHeight = 44.dp,
        cornerRadius = 9.dp,
        colors = ButtonDefaults.textButtonColors(
            color = if (filled) t.accent else t.track,
            disabledColor = if (filled) t.accent else t.track,
            textColor = if (filled) Color.White else t.textSecondary,
            disabledTextColor = if (filled) Color.White else t.textSecondary,
        ),
        textStyle = AppTypography.labelMedium,
    )
}
