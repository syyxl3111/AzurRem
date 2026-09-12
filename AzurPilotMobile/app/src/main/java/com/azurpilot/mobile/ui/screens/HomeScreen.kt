package com.azurpilot.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.components.EmptyCard
import com.azurpilot.mobile.ui.components.FAB_CLEARANCE
import com.azurpilot.mobile.ui.components.Hairline
import com.azurpilot.mobile.ui.components.LargeTitle
import com.azurpilot.mobile.ui.components.ResourceListSkeleton
import com.azurpilot.mobile.ui.components.ResourceRow
import com.azurpilot.mobile.ui.components.SectionTitle
import com.azurpilot.mobile.ui.components.StatusCard
import com.azurpilot.mobile.ui.taskLabel
import com.azurpilot.mobile.ui.theme.AcrylicSurface

/**
 * 主页。
 *
 * 两处刻意的 iOS 结构：
 *  1. 状态卡**吸顶固定**，列表在它下面滚动 —— 否则资源行会滚到状态栏底下和系统时间叠字。
 *  2. 资源不是一叠独立卡片，而是**一个分组容器 + 内缩发丝分隔线**（Settings.app 的写法）。
 */
@Composable
fun HomeScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = insets.horizontal,
                end = insets.horizontal,
                top = insets.top,
            ),
    ) {
        LargeTitle("主页")

        StatusCard(
            instance = state.instance,
            running = state.running,
            stateCode = state.stateCode,
            // MCP 给的是英文任务键，这里过一遍 i18n（权威源是网关 /api/task_tree）
            currentTask = state.taskTree?.nameOf(state.currentTask) ?: taskLabel(state.currentTask),
            lastSyncAt = state.lastSyncAt,
            connected = state.connected,
            refreshing = state.refreshing,
            onRefresh = onRefresh,
        )

        Spacer(Modifier.height(14.dp))
        SectionTitle("资源")

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = insets.bottom + FAB_CLEARANCE, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            when {
                // 首次加载 / 手动刷新且手上没数据 -> 骨架屏。
                // 已经有数据时刷新**不**换骨架，否则每次轮询都会闪一下，比不刷还难受。
                state.resources.isEmpty() && (state.loading || state.refreshing) -> {
                    item(key = "skeleton") { ResourceListSkeleton() }
                }

                state.resources.isEmpty() -> {
                    item(key = "empty") {
                        EmptyCard(
                            title = if (state.connected) "没有读到资源数据" else "还没连上 AzurPilot",
                            detail = state.error ?: "请到「设置」检查服务器地址，或点右上角刷新重试",
                        )
                    }
                }

                else -> {
                    item(key = "resources") {
                        AcrylicSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                state.resources.forEachIndexed { index, res ->
                                    ResourceRow(res)
                                    if (index != state.resources.lastIndex) Hairline()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
