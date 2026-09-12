package com.azurpilot.mobile.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.mobile.ui.components.AcrylicTabBar
import com.azurpilot.mobile.ui.components.ModalConfirmDialog
import com.azurpilot.mobile.ui.components.StartStopFab
import com.azurpilot.mobile.ui.components.TAB_ITEM_HEIGHT
import com.azurpilot.mobile.ui.components.ToastBar
import com.azurpilot.mobile.ui.screens.ConfigScreen
import com.azurpilot.mobile.ui.screens.HomeScreen
import com.azurpilot.mobile.ui.screens.LogsScreen
import com.azurpilot.mobile.ui.screens.SettingsScreen
import com.azurpilot.mobile.ui.screens.StatsScreen
import com.azurpilot.mobile.ui.screens.TaskConfigScreen
import com.azurpilot.mobile.ui.screens.TasksScreen
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.ambientBackground

// Tab 栏高度 = AcrylicTabBar 的项高 + 容器上下 padding(5+5)。
// 用共享常量而不是各写各的，否则改一处忘一处（原来是 52 + 5×2 = 62 手工对上的）。
private val TAB_BAR_HEIGHT = TAB_ITEM_HEIGHT + 10.dp
private val TAB_BAR_GAP = 10.dp
private val SCREEN_PADDING = 14.dp

/** 吸顶状态卡的高度（含下方间隔），FAB 的可拖动范围要避开它 */
private val STATUS_CARD_BLOCK = 84.dp

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()

    val dark = when (state.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    AppTheme(darkTheme = dark) {
        val t = AppTheme.acrylic

        // ── 状态栏 / 导航栏图标跟随**应用内**的主题 ──
        //
        // 真 bug：窗口属性原本由静态 XML（`values/themes.xml` 的 windowLightStatusBar）
        // 决定，而主题是这里用 state.themeMode 覆写的。于是
        // **系统深色 + 应用内选「浅色」⇒ 浅底配白色状态栏图标，直接看不见**。
        // 加剧因素：manifest 的 configChanges 含 uiMode，系统切换深浅色时 Activity 不重建，
        // XML 属性根本不会重新解析。
        //
        // 放 SideEffect 里是因为它要在**每次 dark 变化时**重新应用 —— 这同时也把
        // uiMode 不重建的问题一并解决了（dark 变了就会重组）。
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }

        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        val bottomReserved = TAB_BAR_HEIGHT + TAB_BAR_GAP * 2 + navInset

        val insets = ScreenInsets(
            horizontal = SCREEN_PADDING,
            top = statusInset + 10.dp,
            bottom = bottomReserved + 6.dp,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .ambientBackground(t),
        ) {
            when (val route = state.route) {
                // ── 子页面：全屏压栈，盖住 Tab 栏 ──
                is Route.TaskConfig -> TaskConfigScreen(
                    state = state,
                    task = route.task,
                    onBack = vm::back,
                    onRetry = { vm.loadTaskConfig(route.task) },
                    onSave = vm::saveConfigArg,
                    insets = insets,
                )

                Route.Logs -> LogsScreen(
                    state = state,
                    onRefresh = vm::manualRefresh,
                    onClear = vm::clearLogs,
                    onBack = vm::back,
                    insets = insets,
                )

                // ── Tab 页面 ──
                Route.Tabs -> {
                    Box(Modifier.fillMaxSize()) {
                        when (state.tab) {
                            Tab.Home -> HomeScreen(
                                state = state,
                                onRefresh = vm::manualRefresh,
                                insets = insets,
                            )

                            Tab.Tasks -> TasksScreen(
                                state = state,
                                onRefresh = vm::manualRefresh,
                                onTrigger = vm::requestTrigger,
                                onConfirmTrigger = vm::confirmTrigger,
                                onCancelTrigger = vm::cancelTrigger,
                                onOpenTask = vm::openTaskConfig,
                                insets = insets,
                            )

                            Tab.Config -> ConfigScreen(
                                state = state,
                                onSelectTask = vm::openTaskConfig,
                                onRetry = { vm.loadTaskTree(force = true) },
                                insets = insets,
                            )

                            Tab.Stats -> StatsScreen(
                                state = state,
                                onRefresh = vm::manualRefresh,
                                onCommissionPeriod = { vm.loadCommission(it, force = true) },
                                insets = insets,
                            )

                            Tab.Settings -> SettingsScreen(
                                state = state,
                                onServerUrl = vm::updateServerUrl,
                                onInstance = vm::updateInstance,
                                onWebuiPassword = vm::updateWebuiPassword,
                                onPollSeconds = vm::updatePollSeconds,
                                onLogLines = vm::updateLogLines,
                                onThemeMode = vm::updateThemeMode,
                                onOpenLogs = vm::openLogs,
                                onNotify = vm::notify,
                                onReprefetch = vm::reprefetchAll,
                                onCheckUpdate = vm::checkForUpdate,
                                onDownloadUpdate = vm::downloadUpdate,
                                insets = insets,
                            )
                        }
                    }

                    // ── 启停悬浮按钮：主页和任务页都有 ──
                    // 任务页也放一个，是因为「看一眼队列 -> 决定启停」是最常见的动作路径，
                    // 每次都切回主页很烦（用户明确要求）。
                    if (state.tab == Tab.Home || state.tab == Tab.Tasks) {
                        StartStopFab(
                            modifier = Modifier.fillMaxSize(),
                            running = state.running,
                            stateCode = state.stateCode,
                            busy = state.actionInFlight != null,
                            initialOnRight = state.fabOnRight,
                            initialYFraction = state.fabYFraction,
                            topReserved = insets.top + STATUS_CARD_BLOCK,
                            bottomReserved = insets.bottom,
                            onClick = vm::onFabClick,
                            onMoved = vm::onFabMoved,
                        )
                    }

                    // ── 底部渐隐幕 ──
                    // 内容滚到 Tab 栏之前先淡出。没有这层的话，因为不做真模糊，
                    // Tab 栏会透出下面的文字/数字，非常脏。
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bottomReserved + 26.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        t.bgBottom.copy(alpha = 0.92f),
                                        t.bgBottom,
                                    ),
                                ),
                            ),
                    )

                    AcrylicTabBar(
                        current = state.tab,
                        onSelect = vm::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = SCREEN_PADDING + 2.dp,
                                end = SCREEN_PADDING + 2.dp,
                                bottom = navInset + TAB_BAR_GAP,
                            )
                            .fillMaxWidth(),
                    )

                    if (state.confirmStop) {
                        ModalConfirmDialog(
                            title = "确认停止 ${state.instance}？",
                            detail = "会按 AzurPilot 的「停止后动作」配置收尾（回主页 / 关游戏 / 关模拟器）。",
                            confirmText = "停止",
                            danger = true,
                            onConfirm = vm::confirmStop,
                            onCancel = vm::cancelStop,
                        )
                    }
                }
            }

            // ── 轻提示：**从上方下来的胶囊**（子页面也要能看到）──
            // 放上面有两个理由：iOS 的 HUD 本来就在顶部；底部会被 Tab 栏和悬浮启停按钮盖住。
            state.toast?.let { message ->
                ToastBar(
                    message = message,
                    tone = state.toastTone,
                    onDismiss = vm::consumeToast,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            start = SCREEN_PADDING,
                            end = SCREEN_PADDING,
                            top = statusInset + 8.dp,
                        ),
                )
            }
        }
    }
}
