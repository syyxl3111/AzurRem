package com.azurpilot.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.ui.theme.AppTypography
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.MiuixSurface
import top.yukonga.miuix.kmp.basic.Text

/** 通用空态 / 异常态卡片 */
@Composable
fun EmptyCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.colors
    MiuixSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = AppTypography.titleSmall,
                color = t.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = AppTypography.bodySmall,
                color = t.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 骨架屏占位块 —— 首次加载时用，避免先闪一个 0 再跳成真实值。
 */
@Composable
fun MiuixSurfacePlaceholder(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    MiuixSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(alpha),
        cornerRadius = 14.dp,
    ) {
        Box(Modifier.fillMaxSize())
    }
}
