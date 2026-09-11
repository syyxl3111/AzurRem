package com.azurpilot.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.data.ScheduledTask
import com.azurpilot.mobile.data.splitOverview
import com.azurpilot.mobile.data.splitQueue
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.components.BubbleConfirm
import com.azurpilot.mobile.ui.components.CountBadge
import com.azurpilot.mobile.ui.components.EmptyCard
import com.azurpilot.mobile.ui.components.FAB_CLEARANCE
import com.azurpilot.mobile.ui.components.Hairline
import com.azurpilot.mobile.ui.components.LargeTitle
import com.azurpilot.mobile.ui.components.SectionTitle
import com.azurpilot.mobile.ui.components.SkeletonBar
import com.azurpilot.mobile.ui.components.StatusCard
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.shortDateTime
import com.azurpilot.mobile.ui.taskLabel
import com.azurpilot.mobile.ui.theme.AcrylicSurface
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.NumeralSmall

/**
 * 任务页。
 *
 * **三段，和 PC 概览页一一对应**：运行中 / 队列中 / 等待中
 * （PC 的界面骨架 `app_overview.py:64-87`，填充逻辑 `app_dashboard.py:35-58`）。
 *
 * 分桶由 `splitOverview()` 做（逐行对齐 PC），其中最容易被忽略的一条是：
 * **实例没在跑时，「运行中」是空的，而所有逾期任务全部留在「队列中」**。
 *
 * 行**整行可点**进入该任务的配置页（移动端的习惯，所以不额外挂「设置」按钮）；
 * 右侧的闪电负责「立即执行」，但它**不直接执行** —— 先给一个确认框。
 */
@Composable
fun TasksScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onTrigger: (String) -> Unit,
    onConfirmTrigger: () -> Unit,
    onCancelTrigger: () -> Unit,
    onOpenTask: (String) -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
) {
    // 桥在线就用桥的（已按优先级排好序）；桥不可达退回 MCP 的扁平队列，
    // 顺序可能和 PC 不同，但至少不漏任务。
    val buckets = remember(
        state.overviewLoaded,
        state.overviewPending,
        state.overviewWaiting,
        state.running,
        state.queue,
    ) {
        if (state.overviewLoaded) {
            splitOverview(state.running, state.overviewPending, state.overviewWaiting)
        } else {
            val (p, w) = splitQueue(state.queue)
            splitOverview(state.running, p, w)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = insets.horizontal,
                end = insets.horizontal,
                top = insets.top,
            ),
    ) {
        LargeTitle("任务")

        StatusCard(
            instance = state.instance,
            running = state.running,
            stateCode = state.stateCode,
            // MCP 给的是英文任务键，这里过一遍 i18n（权威源是数据桥 /api/task_tree）
            currentTask = state.taskTree?.nameOf(state.currentTask) ?: taskLabel(state.currentTask),
            lastSyncAt = state.lastSyncAt,
            connected = state.connected,
            refreshing = state.refreshing,
            onRefresh = onRefresh,
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // 别忘了给右下角的悬浮启停按钮留位置，否则滚到底时最后一行会被压住
            contentPadding = PaddingValues(bottom = insets.bottom + FAB_CLEARANCE, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            val empty = buckets.running.isEmpty() &&
                buckets.pending.isEmpty() &&
                buckets.waiting.isEmpty()

            when {
                state.loading && empty && !state.overviewLoaded -> {
                    items(2) { TaskListSkeleton() }
                }

                empty -> {
                    item {
                        EmptyCard(
                            title = "调度队列是空的",
                            detail = "没有已启用的任务。到配置页打开任务开关后会自动出现在这里。",
                        )
                    }
                }

                else -> {
                    // ── 运行中 ──
                    item(key = "runningTitle") {
                        SectionTitle(text = "运行中", trailing = { CountBadge(buckets.running.size) })
                    }
                    item(key = "runningCard") {
                        if (buckets.running.isEmpty()) {
                            EmptyCard(
                                title = "当前没有任务在运行",
                                detail = if (state.running) {
                                    "调度器在跑，但暂时没有到点的任务 —— 见下方「等待中」。"
                                } else {
                                    "实例已停止。启动后第一个到点的任务会出现在这里。"
                                },
                            )
                        } else {
                            TaskGroupCard(
                                tasks = buckets.running,
                                state = state,
                                onTrigger = onTrigger,
                                onOpenTask = onOpenTask,
                            )
                        }
                    }

                    // ── 队列中 ──
                    item(key = "pendingTitle") {
                        Spacer(Modifier.height(4.dp))
                        SectionTitle(text = "队列中", trailing = { CountBadge(buckets.pending.size) })
                    }
                    item(key = "pendingCard") {
                        if (buckets.pending.isEmpty()) {
                            EmptyCard(
                                title = "没有到点的任务",
                                detail = "所有已启用任务的执行时间都还没到，见下方「等待中」。",
                            )
                        } else {
                            TaskGroupCard(
                                tasks = buckets.pending,
                                state = state,
                                onTrigger = onTrigger,
                                onOpenTask = onOpenTask,
                            )
                        }
                    }

                    // ── 等待中 ──
                    item(key = "waitingTitle") {
                        Spacer(Modifier.height(4.dp))
                        SectionTitle(text = "等待中", trailing = { CountBadge(buckets.waiting.size) })
                    }
                    item(key = "waitingCard") {
                        if (buckets.waiting.isEmpty()) {
                            EmptyCard(title = "没有等待中的任务", detail = "队列已排空。")
                        } else {
                            TaskGroupCard(
                                tasks = buckets.waiting,
                                state = state,
                                onTrigger = onTrigger,
                                onOpenTask = onOpenTask,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 确认框：是否立即行动（屏幕居中）──
    state.confirmTrigger?.let { task ->
        val name = state.taskTree?.nameOf(task) ?: taskLabel(task)
        BubbleConfirm(
            text = "立即执行「$name」？",
            confirmText = "立即行动",
            onConfirm = onConfirmTrigger,
            onCancel = onCancelTrigger,
        )
    }
}

@Composable
private fun TaskGroupCard(
    tasks: List<ScheduledTask>,
    state: AppUiState,
    onTrigger: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    AcrylicSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            tasks.forEachIndexed { index, task ->
                TaskRow(
                    task = task,
                    // 中文名优先取任务树（menu.json + i18n，93 个任务全有），
                    // 拿不到才退回内置映射表，最后才是英文键
                    taskName = task.name
                        ?: state.taskTree?.nameOf(task.task)
                        ?: taskLabel(task.task),
                    busy = state.actionInFlight == "trigger:${task.task}",
                    enabled = state.actionInFlight == null,
                    onOpen = { onOpenTask(task.task) },
                    onTrigger = { onTrigger(task.task) },
                )
                if (index != tasks.lastIndex) Hairline(startInset = 16.dp)
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: ScheduledTask,
    taskName: String,
    busy: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onTrigger: () -> Unit,
) {
    val t = AppTheme.acrylic
    val interaction = remember { MutableInteractionSource() }
    // 没有箭头了，必须给按压反馈 —— 否则看不出整行可点
    val pressed by interaction.collectIsPressedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (pressed) t.track else Color.Transparent)
            // 整行点击进配置（移动端的习惯，所以不挂「设置」按钮，也不放箭头）
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
            )
            .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = taskName,
                style = MaterialTheme.typography.bodyLarge,
                color = t.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = shortDateTime(task.nextRun),
                style = MaterialTheme.typography.bodySmall,
                color = t.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onTrigger,
            enabled = enabled,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = AppIcons.Bolt,
                contentDescription = "立即执行 $taskName",
                tint = when {
                    !enabled -> t.textTertiary.copy(alpha = 0.4f)
                    busy -> t.warning
                    else -> t.accent
                },
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** 任务列表的骨架：形状照着真实行来（一行标题 + 一行时间），不是随便一块灰 */
@Composable
private fun TaskListSkeleton() {
    AcrylicSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            repeat(3) { index ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SkeletonBar(width = if (index % 2 == 0) 108.dp else 84.dp, height = 13.dp)
                        Spacer(Modifier.height(7.dp))
                        SkeletonBar(width = 62.dp, height = 10.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    SkeletonBar(width = 17.dp, height = 17.dp, corner = 6.dp)
                }
                if (index != 2) Hairline(startInset = 16.dp)
            }
        }
    }
}
